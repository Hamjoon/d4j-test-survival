"""Freeze developer sources, compile immutable files and measure method baselines."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
from pipeline import CATEGORIES
from run_class import run_class

OUT = Path('results')
JARS = 'tools/junit-4.13.2.jar:tools/hamcrest-core-1.3.jar'


def save(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def exports(bug, prop):
    return (OUT / 'd4j-export' / f'Lang-{bug}b.{prop}').read_text().strip()


def compile_file(source, classes, cp, log):
    classes.mkdir(parents=True, exist_ok=True)
    log.mkdir(parents=True, exist_ok=True)
    command = ['javac', '-d', str(classes), '-cp', cp + ':' + JARS, str(source)]
    start = time.monotonic()
    result = subprocess.run(command, capture_output=True, text=True)
    (log / 'javac.err').write_text(result.stderr)
    (log / 'javac.out').write_text(result.stdout)
    errors = [line for line in result.stderr.splitlines() if re.search(r'\berror:', line)]
    categories = Counter(next((name for name, pattern in CATEGORIES if re.search(pattern, line)), 'other') for line in errors)
    info = dict(command=command, compile_ok=result.returncode == 0, exit_status=result.returncode,
                seconds=round(time.monotonic() - start, 3), error_count=len(errors), categories=dict(categories),
                stderr_file=str(log / 'javac.err'))
    save(log / 'compile.json', info)
    return info


def dev_baseline(records):
    start = time.monotonic()
    rows = []
    for record in records:
        bug = record['bug_id']
        cp = exports(bug, 'cp.test')
        names = exports(bug, 'tests.relevant').splitlines()
        triggers = exports(bug, 'tests.trigger').splitlines()
        root = Path('devtests') / f'Lang-{bug}'
        classes = root / 'classes-t'
        row = dict(bug_id=bug, **{'class': record['class']}, population='dev', classes=[], triggers=triggers)
        rows.append(row)
        for fqcn in names:
            rel = Path(fqcn.replace('.', '/') + '.java')
            original = Path('d4j') / f'Lang-{bug}b' / exports(bug, 'dir.src.tests') / rel
            source = root / rel
            item = dict(fqcn=fqcn, original=str(original), file=str(source), missing=not original.is_file())
            row['classes'].append(item)
            if item['missing']:
                continue
            source.parent.mkdir(parents=True, exist_ok=True)
            if source.exists():
                assert source.read_bytes() == original.read_bytes(), f'Refuse editing developer test {source}'
            else:
                source.write_bytes(original.read_bytes())
            item['sha256'] = digest(source)
            log = root / 'logs-t' / fqcn
            item['compile'] = compile_file(source, classes, cp, log)
        counts = Counter()
        for item in row['classes']:
            if item['missing'] or not item['compile']['compile_ok']:
                continue
            runtime_cp = f'{classes}:tools/runner:{cp}:{JARS}'
            output = root / 'logs-t' / item['fqcn'] / 'run-t.jsonl'
            run = run_class(item['fqcn'], runtime_cp, output, working_directory=Path('d4j') / f'Lang-{bug}b')
            item.update(run_file=str(output), listed=len(run['listed_methods']), counts=run['counts'],
                        methods=run['methods'], run_seconds=run['wall_seconds'])
            assert digest(Path(item['file'])) == item['sha256']
            counts.update(run['counts'])
            print(f'dev Lang-{bug} {item["fqcn"]}: {run["counts"]}', flush=True)
        status_map = {item['fqcn'] + '::' + method['method']: method['status']
                      for item in row['classes'] for method in item.get('methods', [])}
        row.update(class_count=len(names), missing=sum(c['missing'] for c in row['classes']),
                   compiled=sum(c.get('compile', {}).get('compile_ok', False) for c in row['classes']),
                   listed=sum(c.get('listed', 0) for c in row['classes']), counts=dict(counts), D_r=counts['pass'],
                   trigger_statuses={name: status_map.get(name, 'not-listed') for name in triggers},
                   failing_at_t=counts['fail'] + counts['error'],
                   trigger_fail_error=sum(status_map.get(name) in {'fail', 'error'} for name in triggers))
        save(OUT / 'p2-dev-baseline.json', dict(records=rows, complete=False))
    result = dict(records=rows, complete=True, wall_seconds=round(time.monotonic() - start, 3))
    save(OUT / 'p2-dev-baseline.json', result)
    return result


def llm_baseline():
    start = time.monotonic()
    rows = json.loads((OUT / 'p2-extraction-round1.json').read_text())
    result = []
    for extracted in rows:
        row = {k: v for k, v in extracted.items() if k != 'combine_v2'}
        result.append(row)
        if not row['csr_v2']:
            continue
        bug = row['bug_id']
        source = Path(row['file'])
        assert digest(source) == row['extracted_sha256']
        folder, cp = source.parent, exports(bug, 'cp.test')
        classes = folder / 'classes'
        row['compile'] = compile_file(source, classes, cp, folder)
        if row['compile']['compile_ok']:
            runtime_cp = f'{classes}:tools/runner:{cp}:{JARS}'
            output = folder / 'run-t.jsonl'
            run = run_class(row['fqcn'], runtime_cp, output, working_directory=Path('d4j') / f'Lang-{bug}b')
            row.update(run_file=str(output), listed=len(run['listed_methods']), counts=run['counts'],
                       methods=run['methods'], run_seconds=run['wall_seconds'])
            print(f'llm Lang-{bug} {row["technique"]}: {run["counts"]}', flush=True)
        else:
            print(f'llm Lang-{bug} {row["technique"]}: compile failure {row["compile"]["categories"]}', flush=True)
        assert digest(source) == row['extracted_sha256']
        save(OUT / 'p2-llm-baseline-round1.json', dict(files=result, complete=False))
    report = dict(files=result, complete=True, wall_seconds=round(time.monotonic() - start, 3))
    save(OUT / 'p2-llm-baseline-round1.json', report)
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('population', choices=['dev', 'llm'])
    args = parser.parse_args()
    records = sorted(json.loads((OUT / 'lang-records.json').read_text()), key=lambda r: r['bug_id'])
    if args.population == 'dev':
        dev_baseline(records)
    else:
        llm_baseline()


if __name__ == '__main__':
    main()
