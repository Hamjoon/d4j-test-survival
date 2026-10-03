"""Audit provenance and method populations before the mandatory Step 3 stop."""
from collections import Counter
import csv
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
from extract_v2 import extract


def load(path):
    return json.loads(Path(path).read_text())


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def check_run(item, population):
    path = Path(item['run_file'])
    summary = load(path.with_suffix('.summary.json'))
    methods = [json.loads(line) for line in path.read_text().splitlines()]
    assert methods == summary['methods'] == item['methods']
    assert len(methods) == len(summary['listed_methods']) == item['listed']
    assert {m['method'] for m in methods} == set(summary['listed_methods'])
    assert len({m['method'] for m in methods}) == len(methods)
    assert dict(Counter(m['status'] for m in methods)) == summary['counts'] == item['counts']
    assert summary['timeout_ms'] == 30000
    expected_first = (Path(item['file']).parent / 'classes' if population == 'llm'
                      else Path('devtests') / f'Lang-{item["bug_id"]}' / 'classes-t')
    assert summary['classpath'].split(':')[:2] == [str(expected_first.resolve()), str(Path('tools/runner').resolve())]
    assert summary['cwd'] == str((Path('d4j') / f'Lang-{item["bug_id"]}b').resolve())
    assert (expected_first / (item['fqcn'].replace('.', '/') + '.class')).is_file()
    measured = set()
    for launch in summary['launches']:
        command = launch['command']
        excluded = set(command[command.index('--exclude') + 1].split(',')) if '--exclude' in command else set()
        assert excluded == measured
        for m in methods:
            if m['launch'] == launch['launch']:
                measured.add(m['method'])
    assert measured == {m['method'] for m in methods}
    return len(methods)


def main():
    extraction = load('results/p2-extraction-round1.json')
    with open('results/p2-extraction-round1.csv', newline='') as stream:
        extracted_csv = list(csv.DictReader(stream))
    assert len(extraction) == len(extracted_csv) == 70
    sources = 0
    for row, csv_row in zip(extraction, extracted_csv):
        assert sha(row['response']) == row['response_sha256']
        result = extract(Path(row['response']).read_bytes().decode(), row['csr_success'])
        assert all(row[k] == v for k, v in result.items())
        assert csv_row['combine_v2'] == row['combine_v2']
        if row['csr_v2']:
            assert Path(row['file']).read_bytes() == row['combine_v2'].encode()
            assert sha(row['file']) == row['extracted_sha256']
            sources += 1
    assert sources >= 50
    timeline = load('results/p2-timeline.json')
    assert len(timeline['versions']) == 28 and all(v['compile_ok'] for v in timeline['versions'].values())
    parse = lambda value: datetime.strptime(value, '%Y-%m-%d %H:%M:%S %z')
    point_count = 0
    for row in timeline['records']:
        assert row['timepoints'][0]['id'] == f'Lang-{row["bug_id"]}f'
        later = sorted([v for v in timeline['versions'].values() if v['kind'] == 'buggy' and parse(v['date']) > parse(row['date_t'])], key=lambda v: parse(v['date']))
        assert [p['id'] for p in row['timepoints'][1:]] == [v['id'] for v in later]
        for point in row['timepoints']:
            assert point['status'] == ('present' if (Path('d4j') / point['id'] / point['source_path']).is_file() else 'absent')
            actual_gap = (parse(point['date']) - parse(row['date_t'])).total_seconds() / 86400
            assert abs(actual_gap - point['days_after_t']) < 1e-8
            point_count += 1
    dev = load('results/p2-dev-baseline.json')
    llm = load('results/p2-llm-baseline-round1.json')
    comparison = load('results/p2-baseline-comparison.json')
    assert dev['complete'] and llm['complete'] and len(dev['records']) == 14 and len(llm['files']) == 70
    dev_sources = dev_methods = llm_methods = 0
    for row in dev['records']:
        counts = Counter()
        for item in row['classes']:
            if item['missing']:
                continue
            assert sha(item['file']) == sha(item['original']) == item['sha256']
            dev_sources += 1
            if item['compile']['compile_ok']:
                dev_methods += check_run(dict(bug_id=row['bug_id'], **item), 'dev')
                counts.update(item['counts'])
        assert dict(counts) == row['counts'] and counts['pass'] == row['D_r']
    for item in llm['files']:
        if item.get('compile', {}).get('compile_ok'):
            llm_methods += check_run(item, 'llm')
    for row in comparison:
        assert row['D_r'] == next(r['D_r'] for r in dev['records'] if r['bug_id'] == row['bug_id'])
        assert row['L_r_1'] == sum(f.get('counts', {}).get('pass', 0) for f in llm['files'] if f['bug_id'] == row['bug_id'])
        assert row['target_reached'] == (row['L_r_1'] >= row['D_r'])
    key = os.environ.get('OPENROUTER_API_KEY', '').encode()
    assert key, 'Expected credential supplied through compose env_file for non-disclosing exact-key scan'
    scanned = 0
    for root in ['scripts', 'tools/runner', 'generated/p2', 'devtests', 'results', 'docs', 'runs/lang']:
        for path in Path(root).rglob('*'):
            if path.is_file():
                assert key not in path.read_bytes(), 'Credential found in artifact (value withheld)'
                scanned += 1
    result = dict(passed=True, raw_responses_unchanged=70, structured_sources_unchanged=sources,
                  developer_source_copies_unchanged=dev_sources, compiled_checkouts=28,
                  record_timepoints=point_count, developer_methods=dev_methods, llm_methods=llm_methods,
                  credential_scan_files=scanned,
                  checks=['byte-preserving extraction and CSV reconciliation', 'source SHA-256 provenance',
                          'timeline order, dates, presence and all 28 compiles', 'complete method status accounting',
                          'counts and target reconciliation', 'classpath copy precedence',
                          'timeout relaunch excludes every measured method', 'API key absent from artifacts'])
    Path('results/p2-validation.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
