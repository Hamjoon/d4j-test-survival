"""Step 5b: extend the saved ledger only where the unique population is short."""
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
import fcntl
import os
import time
from dedup import write_dedup
from generate_rounds import (OUT, TECHS, MODEL, load, checkpoint, generate_call,
                             evaluate, make_round, log)


def refresh(state, targets):
    # New rows need the full target before dedup annotates them.
    for row in state['rounds']:
        row['D_r'] = targets[row['bug_id']]
    _, records = write_dedup(state)
    state['cumulative_unique_passing'] = {str(r['bug_id']): r['L_r_unique'] for r in records}
    checkpoint(state, targets)


def main():
    state = load(OUT / 'p2-generation-rounds.json')
    if state.get('dedup_complete'):
        log('Step 5b generation already complete; no calls repeated.')
        return
    policy = load(OUT / 'p2-approved-policy.json')
    assert policy['authorized_through'] == 'Step 9'
    assert (policy['model'], policy['temperature'], policy['max_tokens'], policy['max_rounds']) == (MODEL, 0.7, 4096, 30)
    assert policy['message_roles'] == ['user'] and policy['max_concurrency'] == 4
    key = os.environ.get('OPENROUTER_API_KEY')
    if not key:
        raise RuntimeError('Missing OPENROUTER_API_KEY from compose env_file')
    records = sorted(load(OUT / 'lang-records.json'), key=lambda r: r['bug_id'])
    targets = {r['bug_id']: r['D_r'] for r in load(OUT / 'p2-dev-baseline.json')['records']}
    session = dict(started_at=datetime.now(timezone.utc).isoformat())
    state.setdefault('dedup_sessions', []).append(session)
    started = time.monotonic()
    refresh(state, targets)
    try:
        for record in records:
            bug = record['bug_id']
            while state['cumulative_unique_passing'][str(bug)] < targets[bug]:
                number = max(r['round'] for r in state['rounds'] if r['bug_id'] == bug) + 1
                if number > 30:
                    break
                log(f'Start Lang-{bug} r{number}: unique={state["cumulative_unique_passing"][str(bug)]}, D_r={targets[bug]}')
                state['active_round'] = dict(bug_id=bug, round=number, completed_techniques=[])
                checkpoint(state, targets)
                begin = time.monotonic()
                roots = {}
                with ThreadPoolExecutor(max_workers=4) as pool:
                    futures = {pool.submit(generate_call, record, tech, number, key): tech for tech in TECHS}
                    for future in as_completed(futures):
                        tech = futures[future]
                        roots[tech] = future.result()
                        state['active_round']['completed_techniques'].append(tech)
                        checkpoint(state, targets)
                generation_seconds = time.monotonic() - begin
                begin = time.monotonic()
                files = [evaluate(record, tech, number, roots[tech]) for tech in TECHS]
                state['files'].extend(files)
                state['rounds'].append(make_round(record, number, files, generation_seconds, time.monotonic() - begin))
                state.pop('active_round', None)
                refresh(state, targets)
                log(f'Lang-{bug} r{number} complete: unique={state["cumulative_unique_passing"][str(bug)]}/{targets[bug]}, '
                    f'global cost=${state["reported_cost_usd"]:.9f}')
        state['dedup_complete'] = True
        state['dedup_completed_at'] = datetime.now(timezone.utc).isoformat()
    except Exception as exc:
        state['errors'].append(dict(step='5b', type=type(exc).__name__, message=str(exc)))
        raise
    finally:
        session['wall_seconds'] = round(time.monotonic() - started, 3)
        session['ended_at'] = datetime.now(timezone.utc).isoformat()
        state['dedup_wall_seconds'] = round(sum(s['wall_seconds'] for s in state['dedup_sessions'] if 'wall_seconds' in s), 3)
        refresh(state, targets)


if __name__ == '__main__':
    with (OUT / '.p2-rounds.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        main()
