"""Independent invariants for source coverage, ledger selection and population counts."""
from collections import Counter
import hashlib
import json
from pathlib import Path
from dedup import source_tests
from extract_v2 import TECHS


def main():
    fixture = '''package p; public class A {
@Test(expected=IllegalArgumentException.class) public void one() { assertEquals("a b", "ab"); /* } { */ }
@Test public void renamed() throws Exception { assertEquals("ab","a b"); /* } { */ }
class Nested { @org.junit.Test public void inner(){ if(true) { foo(); } } }
}'''
    found = source_tests(fixture, 'p')
    assert [r['method'] for r in found] == ['one','renamed','inner']
    assert found[0]['body_hash'] == found[1]['body_hash'] == hashlib.sha256(b'assertEquals("ab","ab");/*}{*/').hexdigest()
    assert found[2]['test_class'] == 'p.A$Nested'
    load = lambda p: json.loads(Path(p).read_text())
    state, dedup = load('results/p2-generation-rounds.json'), load('results/p2-dedup.json')
    rows = dedup['methods']
    assert rows == sorted(rows,key=lambda r:(r['bug_id'],r['round'],TECHS.index(r['technique']),r['source_order']))
    seen, unique = {}, []
    for row in rows:
        text = Path(row['file']).read_bytes().decode('utf-8')
        assert text[row['opening_offset']] == '{' and text[row['closing_offset']] == '}'
        normalized = ''.join(text[row['opening_offset']+1:row['closing_offset']].split())
        assert hashlib.sha256(normalized.encode()).hexdigest() == row['body_hash']
        key = row['bug_id'],row['body_hash']
        if row['passed_at_t']:
            assert row['is_duplicate'] == (key in seen)
            if key in seen:
                assert row['kept_from'] == seen[key]
            else:
                seen[key] = f'r{row["round"]}/{row["technique"]}/{row["method"]}'
                unique.append(row)
        else:
            assert not row['is_duplicate'] and not row['kept_from']
    source_files = [f for f in state['files'] if f['csr_v2']]
    assert {r['file'] for r in rows} == {f['file'] for f in source_files}
    assert sum(r['passed_at_t'] for r in rows) == sum(f.get('counts',{}).get('pass',0) for f in source_files)
    # javalang independently identifies declarations where the generated Java parses.
    import javalang
    parsed, invalid = 0, []
    for f in source_files:
        text = Path(f['file']).read_text()
        try:
            tree = javalang.parse.parse(text)
        except (javalang.parser.JavaSyntaxError,javalang.tokenizer.LexerError):
            invalid.append(f['file'])
            continue
        expected = Counter(n.name for _,n in tree.filter(javalang.tree.MethodDeclaration)
                           if any(a.name in {'Test','org.junit.Test'} for a in n.annotations))
        actual = Counter(r['method'] for r in rows if r['file']==f['file'])
        assert expected == actual, (f['file'],expected,actual)
        parsed += 1
    population = load('results/p2-population.json')
    identify = lambda r:(r['bug_id'],r['round'],r['technique'],r.get('test_class',r.get('class')),r['method'])
    assert {identify(r) for r in unique} == {identify(r) for r in population['methods'] if r['population']=='llm'}
    for row in state['rounds']:
        assert row['cumulative_L_r_unique'] == sum(r['bug_id']==row['bug_id'] and r['round']<=row['round'] for r in unique)
    report = dict(complete=True, hashed_methods=len(rows),passing_raw=sum(r['passed_at_t'] for r in rows),
        duplicates_removed=sum(r['is_duplicate'] for r in rows),unique=len(unique),
        structured_files=len(source_files),independent_AST_files=parsed,AST_unparseable_files=invalid,
        checks=['source-body SHA-256','all structured sources covered','all baseline passes located',
                'first passing occurrence in required order','nonpasses never duplicates','population identity equality',
                'cumulative unique counts for every round','independent AST declaration counts where parseable'])
    Path('results/p2-dedup-validation.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))


if __name__=='__main__': main()
