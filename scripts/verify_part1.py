"""Check saved pipeline data for protocol and cross-stage consistency."""
import csv
import hashlib
import json
import os
from pathlib import Path


def csv_rows(path):
    with open(path,encoding='utf-8',newline='') as f: return list(csv.DictReader(f))


def main():
    generation=json.loads(Path('results/generation-summary.json').read_text())
    evaluated=json.loads(Path('results/evaluation-manifest.json').read_text())
    extraction=csv_rows('results/extraction.csv')
    extracted={r['file_path']:r for r in extraction}
    assert len(generation['runs'])==len(evaluated)==len(extraction)==70
    assert len({(r['bug_id'],r['technique']) for r in evaluated})==70
    assert generation['generated']==70 and not generation['errors']
    key=os.environ.get('OPENROUTER_API_KEY','').encode()
    for row in evaluated:
        root=Path(row['directory'])
        request=json.loads((root/'request.json').read_text())
        raw=json.loads((root/'raw-response.json').read_text())
        content=raw['choices'][0]['message'].get('content') or ''
        assert set(request)=={'model','temperature','max_tokens','messages'}
        assert request['model']=='openai/gpt-oss-120b' and request['temperature']==0.7 and request['max_tokens']==4096
        assert len(request['messages'])==1 and request['messages'][0]['role']=='user'
        prompt=Path('prompts/rendered/Lang')/str(row['bug_id'])/row['class']/(row['technique']+'.txt')
        assert request['messages'][0]['content']==prompt.read_text()
        assert (root/'response.md').read_bytes()==content.encode('utf-8')
        assert (root/'raw.java').read_bytes()==(root/'response.md').read_bytes()
        assert json.loads((root/'usage.json').read_text())==raw['usage']
        if row['materialized']:
            expected=extracted[str(root/'raw.java')]['combine'].encode('utf-8')
            actual=Path(row['file']).read_bytes()
            assert actual==expected
            assert hashlib.sha256(actual).hexdigest()==row['extracted_sha256']
        if row.get('compile_ok'): assert row.get('syntax_ok')
        if row['status']=='ran':
            assert row.get('compile_ok') and not row['run_error']
            summary=json.loads((Path(row['file']).parent/'run-summary.json').read_text())['summary']
            assert row['tests_run']==summary['run'] and row['failed']==summary['failures']
        for ratio in row.get('coverage',{}).values():
            if isinstance(ratio,float): assert 0<=ratio<=1
    compile_rows=csv_rows('results/compile.csv'); errors=csv_rows('results/compile-errors.csv')
    assert sum(int(r['error_count']) for r in compile_rows)==len(errors)
    assert len(compile_rows)==sum(r.get('syntax_ok',False) for r in evaluated)
    assert len(csv_rows('results/run.csv'))==sum(r.get('compile_ok',False) for r in evaluated)
    assert len(csv_rows('results/coverage.csv'))==sum(r.get('compile_ok',False) for r in evaluated)
    syntax_rows=csv_rows('results/syntax.csv')
    assert len(syntax_rows)==sum(r['materialized'] for r in evaluated)
    assert Path('results/model-list.json').read_bytes()==Path('results/openrouter-models.json').read_bytes()
    known_script=Path('bundle/authors-extraction/extract_tests.py')
    provenance=json.loads(Path('results/extraction-provenance.json').read_text())
    assert hashlib.sha256(known_script.read_bytes()).hexdigest()==provenance['sha256']
    scanned=0
    if key:
        paths=list(Path('runs').rglob('*'))+list(Path('generated').rglob('*'))+list(Path('results').rglob('*'))+list(Path('docs').rglob('*'))+list(Path('scripts').rglob('*'))
        for p in paths:
            if p.is_file():
                assert key not in p.read_bytes(), 'API credential appeared in an artifact'
                scanned+=1
    result={'passed':True,'generation_requests_verified':70,'extraction_rows':70,'generated_sources_unchanged':sum(r['materialized'] for r in evaluated),'syntax_rows':len(syntax_rows),'compile_rows':len(compile_rows),'compile_diagnostic_rows':len(errors),'credential_scan_files':scanned,'checks':['fixed request parameters and exact prompt','verbatim assistant/raw.java','materialized bytes equal combine','source SHA-256 unchanged through compile/run','cross-stage counts and diagnostics','original model list preserved','extractor unchanged','credential absent from artifacts']}
    Path('results/pipeline-validation.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))

if __name__=='__main__': main()
