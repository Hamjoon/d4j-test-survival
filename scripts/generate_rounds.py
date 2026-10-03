"""Approved Part 2 rounds: immutable responses, resumable attempts, full-D_r targets."""
import concurrent.futures
import csv
from datetime import datetime, timezone
from decimal import Decimal
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import threading
import time
import requests
from extract_v2 import TECHS, extract
from p2_baseline import JARS, compile_file, exports
from run_class import run_class

OUT = Path('results')
PRINT_LOCK = threading.Lock()
MODEL = 'openai/gpt-oss-120b'
ENDPOINT = 'https://openrouter.ai/api/v1/chat/completions'


def save(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + '.tmp')
    temp.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')
    temp.replace(path)


def load(path):
    return json.loads(Path(path).read_text())


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def log(message):
    with PRINT_LOCK:
        print(message, flush=True)


def request_body(record, tech):
    prompt = Path('prompts/rendered/Lang') / str(record['bug_id']) / record['class'] / (tech + '.txt')
    return dict(model=MODEL, temperature=0.7, max_tokens=4096,
                messages=[dict(role='user', content=prompt.read_text())])


def complete_attempt(dest, meta):
    result = load(dest / 'raw-response.json')
    if 'error' in result or not result.get('choices'):
        raise RuntimeError(f'No completion in successful HTTP response at {dest}; stop without blind retry')
    choice = result['choices'][0]
    content = choice['message'].get('content') or ''
    for name in ['response.md', 'raw.java']:
        path = dest / name
        data = content.encode('utf-8')
        if path.exists():
            assert path.read_bytes() == data, f'Refuse modifying model output: {path}'
        else:
            path.write_bytes(data)
    save(dest / 'usage.json', result.get('usage', {}))
    meta.update(complete=True, finish_reason=choice.get('finish_reason'), provider=result.get('provider'),
                response_id=result.get('id'), content_empty=not bool(content))
    save(dest / 'run.json', meta)
    return meta


def generate_call(record, tech, round_number, key, post=None, sleeper=time.sleep):
    """The injected HTTP/sleep functions are used only by offline transport checks."""
    post = requests.post if post is None else post
    root = Path('runs/lang') / str(record['bug_id']) / record['class'] / tech / f'r{round_number}'
    body = request_body(record, tech)
    root.mkdir(parents=True, exist_ok=True)
    root_request = root / 'request.json'
    if root_request.exists():
        assert load(root_request) == body, f'Request mismatch at {root}'
    else:
        save(root_request, body)
    if (root / 'run.json').exists() and load(root / 'run.json').get('complete'):
        assert (root / 'response.md').read_bytes() == (root / 'raw.java').read_bytes()
        return root
    for attempt in range(1, 5):
        dest = root / f'attempt-{attempt}'
        dest.mkdir(exist_ok=True)
        meta_path = dest / 'run.json'
        if meta_path.exists():
            meta = load(meta_path)
            assert load(dest / 'request.json') == body
            if meta.get('status') == 200:
                meta = complete_attempt(dest, meta)
            elif isinstance(meta.get('status'), int):
                if attempt == 4:
                    raise RuntimeError(f'HTTP retry limit already reached: {root}')
                # Preserve the prescribed wait even when resuming a failed HTTP attempt.
                sleeper(10)
                continue
            else:
                raise RuntimeError(f'Uncertain prior transport attempt at {dest}; do not duplicate a possibly billed call')
        else:
            save(dest / 'request.json', body)
            meta = dict(timestamp=datetime.now(timezone.utc).isoformat(), status='started', complete=False,
                        attempt=attempt, model=MODEL, bug_id=record['bug_id'], **{'class': record['class']},
                        technique=tech, round=round_number)
            save(meta_path, meta)
            start = time.monotonic()
            try:
                response = post(ENDPOINT, headers={'Authorization': 'Bearer ' + key}, json=body, timeout=360)
            except requests.RequestException as exc:
                meta.update(status='transport_error', exception_type=type(exc).__name__,
                            latency_seconds=round(time.monotonic() - start, 3))
                save(meta_path, meta)
                raise RuntimeError(f'Transport error at {dest}; evidence retained, no blind retry') from None
            (dest / 'raw-response.json').write_bytes(response.content)
            meta.update(status=response.status_code, latency_seconds=round(time.monotonic() - start, 3))
            save(meta_path, meta)
            try:
                parsed = response.json()
            except ValueError:
                parsed = {}
            if isinstance(parsed, dict) and 'usage' in parsed:
                save(dest / 'usage.json', parsed['usage'])
            if not response.ok:
                log(f'HTTP {response.status_code}: Lang-{record["bug_id"]} r{round_number} {tech}, attempt {attempt}')
                if attempt < 4:
                    sleeper(10)
                    continue
                raise RuntimeError(f'HTTP retry limit reached: {root}')
            if response.status_code != 200:
                raise RuntimeError(f'Unexpected successful HTTP status {response.status_code} at {dest}')
            meta = complete_attempt(dest, meta)
        # Canonical files hold the successful attempt; every attempt remains under attempt-N.
        for name in ['request.json', 'raw-response.json', 'response.md', 'raw.java', 'usage.json']:
            source, target = dest / name, root / name
            if target.exists():
                assert target.read_bytes() == source.read_bytes(), f'Refuse replacing call evidence: {target}'
            else:
                shutil.copyfile(source, target)
        save(root / 'run.json', dict(meta, successful_attempt_directory=str(dest)))
        usage = load(root / 'usage.json')
        log(f'Generated Lang-{record["bug_id"]} r{round_number} {tech}: {meta["finish_reason"]}, '
            f'{usage.get("completion_tokens")} tokens, reported cost={usage.get("cost")}')
        return root
    raise AssertionError('Unreachable retry state')


def attempt_cost(root, reused=False):
    directories = [root, *sorted(root.glob('attempt-*'))] if reused else sorted(root.glob('attempt-*'))
    total, missing, attempts = Decimal('0'), 0, 0
    for folder in directories:
        if not (folder / 'run.json').exists():
            continue
        attempts += 1
        usage = load(folder / 'usage.json') if (folder / 'usage.json').exists() else {}
        cost = usage.get('cost')
        if cost is not None:
            total += Decimal(str(cost))
        elif load(folder / 'run.json').get('status') == 200:
            missing += 1
    return dict(reported_cost_usd=float(total), cost_missing_successes=missing, http_attempts=attempts)


def evaluate(record, tech, round_number, root):
    folder = Path('generated/p2') / f'Lang-{record["bug_id"]}' / f'r{round_number}' / tech
    folder.mkdir(parents=True, exist_ok=True)
    result_path = folder / 'evaluation.json'
    if result_path.exists():
        row = load(result_path)
        assert row['response_sha256'] == sha(root / 'response.md')
        if row['file']:
            assert row['extracted_sha256'] == sha(row['file'])
        return row
    result = extract((root / 'response.md').read_bytes().decode('utf-8'))
    save(folder / 'extraction.json', result)
    row = dict(bug_id=record['bug_id'], **{'class': record['class']}, technique=tech, round=round_number,
               directory=str(root), response=str(root / 'response.md'), response_sha256=sha(root / 'response.md'),
               **{k: v for k, v in result.items() if k != 'combine_v2'}, file=None)
    if result['csr_v2']:
        source = folder / (result['class_name'] + '.java')
        data = result['combine_v2'].encode('utf-8')
        if source.exists():
            assert source.read_bytes() == data, f'Refuse editing generated source: {source}'
        else:
            source.write_bytes(data)
        row.update(file=str(source), extracted_sha256=sha(source))
        cp = exports(record['bug_id'], 'cp.test')
        classes = folder / 'classes'
        if (folder / 'compile.json').exists():
            row['compile'] = load(folder / 'compile.json')
        else:
            row['compile'] = compile_file(source, classes, cp, folder)
        if row['compile']['compile_ok']:
            output = folder / 'run-t.jsonl'
            summary_path = output.with_suffix('.summary.json')
            if summary_path.exists():
                summary = load(summary_path)
                assert not summary['list_error'] and len(summary['methods']) == len(summary['listed_methods'])
                assert output.exists()
            else:
                summary = run_class(row['fqcn'], f'{classes}:tools/runner:{cp}:{JARS}', output,
                                    working_directory=Path('d4j') / f'Lang-{record["bug_id"]}b')
            row.update(run_file=str(output), listed=len(summary['listed_methods']), counts=summary['counts'],
                       methods=summary['methods'], run_seconds=summary['wall_seconds'])
        assert sha(source) == row['extracted_sha256']
    save(result_path, row)
    log(f'Evaluated Lang-{record["bug_id"]} r{round_number} {tech}: '
        + (str(row.get('counts', {})) if row.get('compile', {}).get('compile_ok') else
           ('compile failure' if row.get('compile') else 'unstructured')))
    return row


def make_round(record, number, files, generation_seconds=0, evaluation_seconds=0):
    costs = [attempt_cost(Path('runs/lang') / str(record['bug_id']) / record['class'] / t /
                          (f'r{number}' if number > 1 else ''), reused=number == 1) for t in TECHS]
    return dict(bug_id=record['bug_id'], **{'class': record['class']}, round=number, calls=5,
                new_calls=5 if number > 1 else 0, http_attempts=sum(c['http_attempts'] for c in costs),
                csr_v2=sum(f['csr_v2'] for f in files),
                compiled_files=sum(f.get('compile', {}).get('compile_ok', False) for f in files),
                methods_listed=sum(f.get('listed', 0) for f in files),
                methods_passing_at_t=sum(f.get('counts', {}).get('pass', 0) for f in files),
                reported_cost_usd=float(sum(Decimal(str(c['reported_cost_usd'])) for c in costs)),
                cost_missing_successes=sum(c['cost_missing_successes'] for c in costs),
                generation_seconds=round(generation_seconds, 3), evaluation_seconds=round(evaluation_seconds, 3))


def checkpoint(state, targets):
    passes, costs, new_costs = {}, {}, {}
    global_cost = Decimal('0')
    global_new = Decimal('0')
    for index, row in enumerate(state['rounds'], 1):
        bug = row['bug_id']
        passes[bug] = passes.get(bug, 0) + row['methods_passing_at_t']
        cost = Decimal(str(row['reported_cost_usd']))
        costs[bug] = costs.get(bug, Decimal('0')) + cost
        new_costs[bug] = new_costs.get(bug, Decimal('0')) + (cost if row['round'] > 1 else Decimal('0'))
        global_cost += cost
        if row['round'] > 1:
            global_new += cost
        row.update(ledger_index=index, cumulative_L_r=passes[bug], D_r=targets[bug],
                   target_reached=passes[bug] >= targets[bug], cumulative_cost_usd=float(costs[bug]),
                   cumulative_new_cost_usd=float(new_costs[bug]),
                   global_cumulative_cost_usd=float(global_cost), global_cumulative_new_cost_usd=float(global_new))
    state.update(cumulative_passing={str(k): v for k, v in passes.items()},
                 reported_cost_usd=float(global_cost), new_reported_cost_usd=float(global_new))
    save(OUT / 'p2-generation-rounds.json', state)
    fields = list(state['rounds'][0])
    temp = OUT / 'p2-rounds.csv.tmp'
    with temp.open('w', newline='') as stream:
        writer = csv.DictWriter(stream, fields, lineterminator='\n')
        writer.writeheader()
        writer.writerows(state['rounds'])
    temp.replace(OUT / 'p2-rounds.csv')


def main():
    key = os.environ.get('OPENROUTER_API_KEY')
    if not key:
        raise RuntimeError('Missing OPENROUTER_API_KEY from compose env_file')
    policy = load(OUT / 'p2-approved-policy.json')
    config = load(OUT / 'model-config.json')
    assert config['model'] == policy['model'] == MODEL
    assert config['temperature'] == policy['temperature'] == 0.7
    assert config['max_tokens'] == policy['max_tokens'] == 4096
    assert config['message_roles'] == policy['message_roles'] == ['user']
    assert policy['max_concurrency'] == 4 and policy['max_rounds'] == 30
    records = sorted(load(OUT / 'lang-records.json'), key=lambda r: r['bug_id'])
    dev = load(OUT / 'p2-dev-baseline.json')
    baseline = load(OUT / 'p2-llm-baseline-round1.json')
    assert dev['complete'] and baseline['complete']
    targets = {r['bug_id']: r['D_r'] for r in dev['records']}
    eligible = [r['bug_id'] for r in records if sum(f.get('counts', {}).get('pass', 0)
                for f in baseline['files'] if f['bug_id'] == r['bug_id']) < targets[r['bug_id']]]
    assert eligible == policy['records_requiring_extra_rounds']
    state_path = OUT / 'p2-generation-rounds.json'
    if state_path.exists():
        state = load(state_path)
        assert state['model'] == MODEL
        if state['complete']:
            log('Generation rounds already complete; no calls or runs repeated.')
            return
    else:
        rounds = [make_round(r, 1, [f for f in baseline['files'] if f['bug_id'] == r['bug_id']]) for r in records]
        state = dict(model=MODEL, complete=False, max_concurrency=4, max_rounds=30,
                     started_at=datetime.now(timezone.utc).isoformat(), rounds=rounds,
                     files=list(baseline['files']), sessions=[], errors=[])
    session = dict(started_at=datetime.now(timezone.utc).isoformat())
    state['sessions'].append(session)
    started = time.monotonic()
    checkpoint(state, targets)
    try:
        for record in records:
            bug = record['bug_id']
            while int(state['cumulative_passing'][str(bug)]) < targets[bug]:
                prior = [r for r in state['rounds'] if r['bug_id'] == bug]
                number = max(r['round'] for r in prior) + 1
                if number > 30:
                    break
                log(f'Start Lang-{bug} r{number}: L_r={state["cumulative_passing"][str(bug)]}, D_r={targets[bug]}')
                state['active_round'] = dict(bug_id=bug, round=number, completed_techniques=[])
                checkpoint(state, targets)
                begin_generation = time.monotonic()
                roots = {}
                with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
                    futures = {pool.submit(generate_call, record, tech, number, key): tech for tech in TECHS}
                    try:
                        for future in concurrent.futures.as_completed(futures):
                            tech = futures[future]
                            roots[tech] = future.result()
                            state['active_round']['completed_techniques'].append(tech)
                            checkpoint(state, targets)
                    except Exception:
                        for future in futures:
                            future.cancel()
                        raise
                generation_seconds = time.monotonic() - begin_generation
                begin_evaluation = time.monotonic()
                files = [evaluate(record, tech, number, roots[tech]) for tech in TECHS]
                row = make_round(record, number, files, generation_seconds, time.monotonic() - begin_evaluation)
                state['files'].extend(files)
                state['rounds'].append(row)
                state.pop('active_round', None)
                checkpoint(state, targets)
                log(f'Round complete Lang-{bug} r{number}: {row["methods_passing_at_t"]} passes, '
                    f'L_r={row["cumulative_L_r"]}/{targets[bug]}, cumulative record cost=${row["cumulative_cost_usd"]:.9f}')
        state['complete'] = True
        state['completed_at'] = datetime.now(timezone.utc).isoformat()
    except Exception as exc:
        state['errors'].append(dict(timestamp=datetime.now(timezone.utc).isoformat(),
                                    type=type(exc).__name__, message=str(exc)))
        raise
    finally:
        session['wall_seconds'] = round(time.monotonic() - started, 3)
        session['ended_at'] = datetime.now(timezone.utc).isoformat()
        state['wall_seconds'] = round(sum(s.get('wall_seconds', 0) for s in state['sessions']), 3)
        checkpoint(state, targets)
    log(f'Step 4 complete: {sum(r["new_calls"] for r in state["rounds"])} new completions, '
        f'new reported cost=${state["new_reported_cost_usd"]:.9f}. Stop before Step 6.')


if __name__ == '__main__':
    # Shared checkout lock prevents concurrent processes from duplicating paid calls.
    with (OUT / '.p2-rounds.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        main()
