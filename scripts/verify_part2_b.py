"""Audit immutable calls, round stopping, costs and Step 5 population filters."""
from collections import Counter
import csv
from decimal import Decimal
from datetime import datetime, timedelta
import json
import os
from pathlib import Path
from extract_v2 import extract
from generate_rounds import TECHS, attempt_cost, request_body, sha
from p2_population import developer_population
from verify_part2_a import check_run


def load(path):
    return json.loads(Path(path).read_text())


def near(left, right):
    assert abs(Decimal(str(left)) - Decimal(str(right))) < Decimal('0.000000000001'), (left, right)


def identity(method):
    return (method['bug_id'], method['population'], method['round'], method['technique'],
            method['test_class'], method['method'])


def main():
    state = load('results/p2-generation-rounds.json')
    assert state['complete'] and not state['errors'] and 'active_round' not in state
    records = {r['bug_id']: r for r in load('results/lang-records.json')}
    dev = load('results/p2-dev-baseline.json')
    baseline = load('results/p2-llm-baseline-round1.json')
    population = load('results/p2-population.json')
    policy = load('results/p2-approved-policy.json')
    assert state['max_concurrency'] == policy['max_concurrency'] == 4
    assert state['max_rounds'] == policy['max_rounds'] == 30
    assert [f for f in state['files'] if f['round'] == 1] == baseline['files']
    targets = {r['bug_id']: r['D_r'] for r in dev['records']}
    assert len({(f['bug_id'], f['round'], f['technique']) for f in state['files']}) == len(state['files'])
    new_files = [f for f in state['files'] if f['round'] > 1]
    new_requests = new_attempts = 0
    immutable_sources = 0
    expected_llm = set()
    http_events = []
    for file in state['files']:
        assert sha(file['response']) == file['response_sha256']
        if file.get('file'):
            assert sha(file['file']) == file['extracted_sha256']
            immutable_sources += 1
        if file.get('compile', {}).get('compile_ok'):
            check_run(file, 'llm')
        for method in file.get('methods', []):
            if method['status'] == 'pass':
                expected_llm.add((file['bug_id'], 'llm', file['round'], file['technique'], file['fqcn'], method['method']))
        if file['round'] == 1:
            continue
        assert file['bug_id'] in policy['records_requiring_extra_rounds']
        new_requests += 1
        root = Path(file['directory'])
        body = request_body(records[file['bug_id']], file['technique'])
        assert load(root / 'request.json') == body
        original_request = Path('runs/lang') / str(file['bug_id']) / file['class'] / file['technique'] / 'request.json'
        assert body == load(original_request), 'Additional round changed the original request'
        assert set(body) == {'model', 'temperature', 'max_tokens', 'messages'}
        result = load(root / 'raw-response.json')
        content = result['choices'][0]['message'].get('content') or ''
        assert (root / 'response.md').read_bytes() == (root / 'raw.java').read_bytes() == content.encode()
        assert load(root / 'usage.json') == result.get('usage', {})
        extracted = extract(content)
        folder = Path('generated/p2') / f'Lang-{file["bug_id"]}' / f'r{file["round"]}' / file['technique']
        assert load(folder / 'extraction.json') == extracted
        for key, value in extracted.items():
            if key != 'combine_v2':
                assert file[key] == value
        if file['csr_v2']:
            assert Path(file['file']).read_bytes() == extracted['combine_v2'].encode()
        metadata = load(root / 'run.json')
        assert metadata['complete'] and metadata['status'] == 200
        successful = Path(metadata['successful_attempt_directory'])
        for name in ['request.json', 'raw-response.json', 'response.md', 'raw.java', 'usage.json']:
            assert (root / name).read_bytes() == (successful / name).read_bytes()
        attempts = sorted(root.glob('attempt-*/run.json'))
        assert 1 <= len(attempts) <= 4
        assert len(attempts) == metadata['attempt']
        new_attempts += len(attempts)
        for index, meta_path in enumerate(attempts, 1):
            attempt = load(meta_path)
            assert attempt['attempt'] == index
            assert load(meta_path.parent / 'request.json') == body
            assert (meta_path.parent / 'raw-response.json').is_file()
            began = datetime.fromisoformat(attempt['timestamp'])
            http_events.extend([(began, 1), (began + timedelta(seconds=attempt['latency_seconds']), -1)])
    active = peak = 0
    for _, change in sorted(http_events):
        active += change
        peak = max(peak, active)
    assert active == 0 and peak <= 4
    with open('results/p2-rounds.csv', newline='') as stream:
        csv_rows = list(csv.DictReader(stream))
    assert len(csv_rows) == len(state['rounds'])
    global_cost = global_new = Decimal('0')
    per_record_costs, per_record_new, per_record_passes = {}, {}, {}
    for ledger_index, (row, csv_row) in enumerate(zip(state['rounds'], csv_rows), 1):
        assert row['ledger_index'] == ledger_index
        assert all(csv_row[k] == str(v) for k, v in row.items())
        bug, number = row['bug_id'], row['round']
        files = [f for f in state['files'] if f['bug_id'] == bug and f['round'] == number]
        assert sorted(f['technique'] for f in files) == sorted(TECHS)
        assert row['calls'] == 5 and row['new_calls'] == (5 if number > 1 else 0)
        assert row['csr_v2'] == sum(f['csr_v2'] for f in files)
        assert row['compiled_files'] == sum(f.get('compile', {}).get('compile_ok', False) for f in files)
        assert row['methods_listed'] == sum(f.get('listed', 0) for f in files)
        assert row['methods_passing_at_t'] == sum(f.get('counts', {}).get('pass', 0) for f in files)
        if number > 1:
            assert per_record_passes[bug] < targets[bug], 'Generated a round after reaching the target'
        per_record_passes[bug] = per_record_passes.get(bug, 0) + row['methods_passing_at_t']
        assert row['cumulative_L_r'] == per_record_passes[bug] and row['D_r'] == targets[bug]
        assert row['target_reached'] == (row['cumulative_L_r'] >= row['D_r'])
        actual_cost = Decimal('0')
        attempts_count = missing = 0
        for tech in TECHS:
            root = Path('runs/lang') / str(bug) / records[bug]['class'] / tech
            if number > 1:
                root = root / f'r{number}'
            cost = attempt_cost(root, reused=number == 1)
            actual_cost += Decimal(str(cost['reported_cost_usd']))
            attempts_count += cost['http_attempts']
            missing += cost['cost_missing_successes']
        near(row['reported_cost_usd'], actual_cost)
        assert row['http_attempts'] == attempts_count and row['cost_missing_successes'] == missing
        per_record_costs[bug] = per_record_costs.get(bug, Decimal('0')) + actual_cost
        per_record_new[bug] = per_record_new.get(bug, Decimal('0')) + (actual_cost if number > 1 else Decimal('0'))
        global_cost += actual_cost
        if number > 1:
            global_new += actual_cost
        near(row['cumulative_cost_usd'], per_record_costs[bug])
        near(row['cumulative_new_cost_usd'], per_record_new[bug])
        near(row['global_cumulative_cost_usd'], global_cost)
        near(row['global_cumulative_new_cost_usd'], global_new)
    near(state['reported_cost_usd'], global_cost)
    near(state['new_reported_cost_usd'], global_new)
    for bug in records:
        rows = [r for r in state['rounds'] if r['bug_id'] == bug]
        assert [r['round'] for r in rows] == list(range(1, len(rows) + 1))
        assert len(rows) <= 30 and (rows[-1]['target_reached'] or len(rows) == 30)
        if bug not in policy['records_requiring_extra_rounds']:
            assert len(rows) == 1
    actual_dev_summaries, expected_dev = developer_population(list(records.values()), dev)
    assert load('results/p2-dev-own-baseline.json') == dict(records=actual_dev_summaries, extra_compilations=0, extra_runs=0)
    actual_dev = [m for m in population['methods'] if m['population'] == 'dev']
    actual_llm = [m for m in population['methods'] if m['population'] == 'llm']
    assert actual_dev == expected_dev
    assert {identity(m) for m in actual_llm} == expected_llm
    assert len({identity(m) for m in population['methods']}) == len(population['methods'])
    for method in population['methods']:
        assert type(method['own_class']) is bool
        assert method['baseline_status'] == 'pass'
        assert sha(method['file']) == method['source_sha256']
    for row in population['records']:
        bug = row['bug_id']
        dm = [m for m in actual_dev if m['bug_id'] == bug]
        lm = [m for m in actual_llm if m['bug_id'] == bug]
        assert row['D_r'] == len(dm) == targets[bug]
        assert row['D_r_own'] == sum(m['own_class'] for m in dm)
        assert row['L_r'] == len(lm) == per_record_passes[bug]
        assert row['technique_counts'] == {t: sum(m['technique'] == t for m in lm) for t in TECHS}
    assert next(r for r in population['records'] if r['bug_id'] == 57)['L_r'] == 139
    assert next(r for r in population['records'] if r['bug_id'] == 57)['D_r'] == 0
    assert population['full_dev_2x2_excluded_records'] == [57]
    assert population['dev_own_2x2_empty_records'] == [6, 17, 28, 57]
    assert population['reporting_policy'] == policy
    for record in dev['records']:
        for cls in record['classes']:
            if not cls['missing']:
                assert sha(cls['original']) == sha(cls['file']) == cls['sha256']
    key = os.environ.get('OPENROUTER_API_KEY', '').encode()
    assert key, 'Use compose env_file for the non-disclosing credential check'
    scanned = 0
    for name in ['runs', 'generated/p2', 'devtests', 'results', 'docs', 'scripts', 'tools/runner']:
        for path in Path(name).rglob('*'):
            if path.is_file():
                assert key not in path.read_bytes(), 'Credential found in artifact (value withheld)'
                scanned += 1
    report = dict(passed=True, new_completions=new_requests, new_http_attempts=new_attempts,
        max_overlapping_http_attempts=peak,
        reused_completions=70, immutable_llm_sources=immutable_sources,
        developer_population=len(actual_dev), developer_own_population=sum(m['own_class'] for m in actual_dev),
        llm_population=len(actual_llm), records_at_target=sum(r['target_reached'] for r in population['records']),
        new_reported_cost_usd=float(global_new), reported_cost_including_reused_usd=float(global_cost),
        credential_scan_files=scanned,
        checks=['exact request parameters and rendered prompts', 'raw assistant responses and canonical attempt copies',
                'source bytes and hashes unchanged', 'round-1 artifacts reused', 'per-method run accounting',
                'full-D_r stopping and 30-round cap', 'record and global cost ledgers',
                'dev-own filter without extra runs', 'Lang-57 retained with required reporting policy',
                'population method identities', 'API credential absent from artifacts'])
    Path('results/p2-validation-b.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
