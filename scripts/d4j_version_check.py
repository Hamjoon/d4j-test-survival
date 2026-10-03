"""Compare author class sources with buggy/fixed Defects4J checkouts."""

import difflib
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path('/work')
TARGETS = [('Lang', '4'), ('Csv', '1'), ('Cli', '5'), ('Closure', '4'), ('Mockito', '1')]


def normalize(text):
    text = text.replace('\r\n', '\n').encode('ascii', errors='ignore').decode('ascii')
    lines = [line.rstrip() for line in text.split('\n')]
    while lines and not lines[-1]:
        lines.pop()
    return '\n'.join(lines)


def changed_lines(left, right):
    matcher = difflib.SequenceMatcher(None, left.split('\n'), right.split('\n'), autojunk=False)
    return sum(max(a1 - a0, b1 - b0) for op, a0, a1, b0, b1 in matcher.get_opcodes() if op != 'equal')


def source_root(project, bug, side):
    checkout = ROOT / 'd4j' / f'{project}-{bug}{side}'
    result = subprocess.run(['defects4j', 'export', '-p', 'dir.src.classes', '-w', str(checkout)], text=True, capture_output=True, check=True)
    return checkout / result.stdout.strip()


def main():
    records = json.loads((ROOT / 'bundle/Defects4J-dataset.json').read_text(encoding='utf-8', errors='ignore'))
    rows, diffs = [], []
    for project, bug in TARGETS:
        roots = {side: source_root(project, bug, side) for side in ['b', 'f']}
        selected = [(i, r) for i, r in enumerate(records) if r['project_name'] == project and str(r['bug-id']) == bug]
        if not selected:
            raise RuntimeError(f'No dataset records found for {project}-{bug}')
        for index, record in selected:
            source = record['source_code']
            package = re.search(r'^\s*package\s+([\w.]+)\s*;', source, re.MULTILINE)
            relative = Path(*package.group(1).split('.')) if package else Path()
            relative = relative / f"{record['class']}.java"
            row = {'record_index': index, 'project': project, 'bug_id': bug, 'class': record['class'], 'relative_java_path': str(relative)}
            paths = {side: root / relative for side, root in roots.items()}
            missing = [str(path) for path in paths.values() if not path.is_file()]
            if missing:
                row['skipped'] = 'No standalone Java file (possible nested/non-public class): ' + ', '.join(missing)
                rows.append(row)
                continue
            texts = {'json': normalize(source)}
            texts.update({side: normalize(path.read_bytes().decode('utf-8', errors='ignore')) for side, path in paths.items()})
            row.update({
                'buggy_path': str(paths['b']),
                'fixed_path': str(paths['f']),
                'equals_buggy': texts['json'] == texts['b'],
                'equals_fixed': texts['json'] == texts['f'],
                'buggy_equals_fixed': texts['b'] == texts['f'],
                'differing_lines_buggy': changed_lines(texts['json'], texts['b']),
                'differing_lines_fixed': changed_lines(texts['json'], texts['f']),
                'normalized_sha256': {name: hashlib.sha256(value.encode('ascii')).hexdigest() for name, value in texts.items()},
            })
            rows.append(row)
            if not row['equals_buggy'] and not row['equals_fixed']:
                closer = 'b' if row['differing_lines_buggy'] <= row['differing_lines_fixed'] else 'f'
                diff = list(difflib.unified_diff(texts['json'].splitlines(), texts[closer].splitlines(), fromfile='JSON', tofile=str(paths[closer]), lineterm=''))
                diffs += [f"### {project}-{bug} {record['class']} (closer side: {closer})", '', '```diff', *diff[:60], '```', '']
    checked = [row for row in rows if 'skipped' not in row]
    if checked and len(checked) == len(rows) and all(r['equals_buggy'] and not r['equals_fixed'] for r in checked):
        verdict = 'JSON = buggy'
    elif checked and len(checked) == len(rows) and all(r['equals_fixed'] and not r['equals_buggy'] for r in checked):
        verdict = 'JSON = fixed'
    else:
        verdict = 'mixed/neither'
    lines = [f'# Stage 0 version check', '', f'**Verdict: {verdict}**', '',
             'Comparison converts CRLF to LF, drops non-ASCII characters, strips trailing whitespace per line, and removes trailing blank lines from all three sources. Differing lines are the sum of `max(JSON span, checkout span)` for each non-equal `SequenceMatcher` block (`autojunk=False`).', '',
             '| Record index (0-based) | Project | Bug | Class | equals_buggy | equals_fixed | buggy_equals_fixed | Diff lines vs buggy | Diff lines vs fixed |',
             '|---:|---|---|---|---|---|---|---:|---:|']
    for row in checked:
        keys = ['record_index', 'project', 'bug_id', 'class', 'equals_buggy', 'equals_fixed', 'buggy_equals_fixed', 'differing_lines_buggy', 'differing_lines_fixed']
        lines.append('| ' + ' | '.join(str(row[key]) for key in keys) + ' |')
    lines += ['', 'Paths and normalized SHA-256 hashes are recorded in `stage0-version-check.json`.', '']
    for row in rows:
        if 'skipped' in row:
            lines += [f"Skipped {row['project']}-{row['bug_id']} {row['class']}: {row['skipped']}", '']
    lines += diffs
    output = '\n'.join(lines)
    (ROOT / 'results/stage0-version-check.md').write_text(output)
    (ROOT / 'results/stage0-version-check.json').write_text(json.dumps({'verdict': verdict, 'records': rows}, indent=2) + '\n')
    print(output)
    if verdict == 'mixed/neither':
        raise SystemExit(2)


if __name__ == '__main__':
    main()
