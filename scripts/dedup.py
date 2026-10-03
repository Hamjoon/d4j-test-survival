"""Exact whitespace-stripped source-body hashes; no source edits or AST repair."""
from collections import Counter
import csv
import hashlib
import json
from pathlib import Path
import re
from extract_v2 import TECHS, lexical_masks


def load(path):
    return json.loads(Path(path).read_text())


def save(path, value):
    Path(path).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


def paired(code, opening, closing):
    stack, matches = [], {}
    for i, ch in enumerate(code):
        if ch == opening:
            stack.append(i)
        elif ch == closing and stack:
            matches[stack.pop()] = i
    return matches


def source_tests(text, package):
    # Mask only for locating declarations/braces. Hash the untouched ORIGINAL body.
    _, code = lexical_masks(text)
    braces, parens = paired(code, '{', '}'), paired(code, '(', ')')
    classes = []
    for match in re.finditer(r'\b(?:class|enum|interface)\s+(\w+)[^;{}]*\{', code):
        opening = match.end() - 1
        if opening in braces:
            classes.append((opening, braces[opening], match[1]))
    candidates = []
    for annotation in re.finditer(r'@(?:org\.junit\.)?Test\b', code):
        cursor = annotation.start()
        # Skip annotations, including expected/timeout parameter lists.
        while True:
            match = re.match(r'\s*@[\w.]+\s*', code[cursor:])
            if not match:
                break
            cursor += match.end()
            if cursor < len(code) and code[cursor] == '(':
                if cursor not in parens:
                    raise RuntimeError('Unclosed test annotation')
                cursor = parens[cursor] + 1
        signature = re.match(r'\s*[^;{}()]*?\b(\w+)\s*\(', code[cursor:])
        if not signature:
            raise RuntimeError(f'Cannot locate test signature at offset {annotation.start()}')
        opening_params = cursor + signature.end() - 1
        if opening_params not in parens:
            raise RuntimeError('Unclosed test parameters')
        after = parens[opening_params] + 1
        body_match = re.match(r'\s*(?:throws\s+[^;{}]+)?\s*\{', code[after:])
        if not body_match:
            raise RuntimeError(f'Test method without locatable body: {signature[1]}')
        opening = after + body_match.end() - 1
        candidates.append((opening, signature[1], annotation.start()))
    # JUnit 3 methods, if a response used that style despite importing JUnit 4.
    if re.search(r'\bextends\s+(?:junit\.framework\.)?TestCase\b', code):
        for match in re.finditer(r'\bpublic\s+void\s+(test\w+)\s*\(\s*\)\s*(?:throws\s+[^;{}]+)?\{', code):
            opening = match.end() - 1
            if opening not in {c[0] for c in candidates}:
                candidates.append((opening, match[1], match.start()))
    rows = []
    for index, (opening, name, declaration) in enumerate(sorted(candidates), 1):
        if opening not in braces:
            raise RuntimeError(f'Unclosed test body: {name}')
        closing = braces[opening]
        owners = [c for c in classes if c[0] < declaration < c[1]]
        if not owners:
            raise RuntimeError(f'No containing class for {name}')
        owner_name = '$'.join(c[2] for c in sorted(owners))
        fqcn = (package + '.' if package else '') + owner_name
        body = text[opening + 1:closing]
        normalized = ''.join(ch for ch in body if not ch.isspace())
        rows.append(dict(test_class=fqcn, method=name, source_order=index,
                         opening_offset=opening, closing_offset=closing,
                         opening_line=text.count('\n', 0, opening) + 1,
                         body_hash=hashlib.sha256(normalized.encode('utf-8')).hexdigest()))
    return rows


def calculate(state):
    rows, seen, cumulative = [], {}, {}
    files = sorted(state['files'], key=lambda f: (f['bug_id'], f['round'], TECHS.index(f['technique'])))
    for file in files:
        if not file['csr_v2']:
            continue
        text = Path(file['file']).read_bytes().decode('utf-8')
        assert hashlib.sha256(text.encode()).hexdigest() == file['extracted_sha256']
        tests = source_tests(text, file['package'])
        passing = {m['method'] for m in file.get('methods', []) if m['status'] == 'pass'}
        located_passes = set()
        status = {m['method']: m['status'] for m in file.get('methods', [])}
        for method in tests:
            passed = method['test_class'] == file['fqcn'] and method['method'] in passing
            if passed:
                located_passes.add(method['method'])
            key = (file['bug_id'], method['body_hash'])
            duplicate = passed and key in seen
            kept = seen[key] if duplicate else None
            row = dict(record=f'Lang-{file["bug_id"]}', bug_id=file['bug_id'], round=file['round'],
                       technique=file['technique'], **{'class': method['test_class']},
                       method=method['method'], body_hash=method['body_hash'], passed_at_t=passed,
                       is_duplicate=duplicate, kept_from=kept or '', source_order=method['source_order'],
                       file=file['file'], source_sha256=file['extracted_sha256'],
                       status_at_t=status.get(method['method'], 'compile-fail' if not file.get('compile', {}).get('compile_ok') else 'not-listed'),
                       opening_offset=method['opening_offset'], closing_offset=method['closing_offset'],
                       opening_line=method['opening_line'])
            rows.append(row)
            if passed and not duplicate:
                seen[key] = f'r{file["round"]}/{file["technique"]}/{method["method"]}'
        assert located_passes == passing, f'Passing methods not located exactly: {file["file"]}: {passing - located_passes}'
    for row in state['rounds']:
        group = [r for r in rows if r['bug_id'] == row['bug_id'] and r['round'] <= row['round']]
        cumulative[(row['bug_id'], row['round'])] = sum(r['passed_at_t'] and not r['is_duplicate'] for r in group)
    summary = []
    for bug in sorted({f['bug_id'] for f in files}):
        group = [r for r in rows if r['bug_id'] == bug]
        raw = sum(r['passed_at_t'] for r in group)
        duplicates = sum(r['is_duplicate'] for r in group)
        summary.append(dict(bug_id=bug, methods_hashed=len(group), L_r=raw, duplicates_removed=duplicates,
                            L_r_unique=raw - duplicates))
    return rows, summary, cumulative


def write_dedup(state):
    rows, summary, cumulative = calculate(state)
    with open('results/p2-dedup.csv', 'w', newline='') as stream:
        writer = csv.DictWriter(stream, list(rows[0]), lineterminator='\n')
        writer.writeheader()
        writer.writerows(rows)
    save('results/p2-dedup.json', dict(records=summary, methods=rows,
        rule='SHA-256 of UTF-8 source between method braces, removing every Unicode whitespace character; comments retained',
        duplicate_scope='record; passing occurrences only', ledger_order=['round', *TECHS, 'source order']))
    for row in state['rounds']:
        row['cumulative_L_r_unique'] = cumulative[(row['bug_id'], row['round'])]
        row['target_reached_unique'] = row['cumulative_L_r_unique'] >= row['D_r']
    return rows, summary


if __name__ == '__main__':
    state = load('results/p2-generation-rounds.json')
    _, summary = write_dedup(state)
    for row in summary:
        print(row)
