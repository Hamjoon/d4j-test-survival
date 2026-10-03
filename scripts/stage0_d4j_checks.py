"""Run the requested Stage 0 checkouts, exports, builds, and Lang sanity test."""

import json
import shlex
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path('/work')
RESULTS = ROOT / 'results'
LOGS = RESULTS / 'stage0-logs'
EXPORTS = RESULTS / 'stage0-export'
VERSIONS = ['Lang-4b', 'Lang-4f', 'Csv-1b', 'Csv-1f', 'Cli-5b', 'Cli-5f', 'Closure-4b', 'Closure-4f', 'Mockito-1b', 'Mockito-1f']
PROPERTIES = ['classes.modified', 'dir.src.classes', 'dir.src.tests', 'dir.bin.classes', 'dir.bin.tests', 'cp.compile', 'cp.test']


def command(args, log):
    start = time.perf_counter()
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    elapsed = time.perf_counter() - start
    log.write_text(f'$ {shlex.join(args)}\n\n[stdout]\n{result.stdout}\n[stderr]\n{result.stderr}\n[exit status] {result.returncode}\n[wall seconds] {elapsed:.3f}\n')
    return result, round(elapsed, 3)


def save(data):
    (RESULTS / 'stage0-d4j-checks.json').write_text(json.dumps(data, indent=2) + '\n')
    lines = ['# Stage 0 Defects4J checks', '', f"Started: {data['timestamp_utc']}", '',
             'Per the 2026-10-02 addendum, Closure-1b was checked out and then removed along with its active export file; the initial evidence is archived under `archive/stage0-initial-stop/`. Closure-4b/4f replaces it, and Mockito-1f is included. All ten comparisons are required.', '',
             '| Checkout | classes.modified | JSON classes match | Compile | Wall seconds | dir.src.classes | dir.src.tests | cp.test produced |',
             '|---|---|---|---|---:|---|---|---|']
    for row in data['checkouts']:
        props = row.get('exports', {})
        build = row.get('compile', {})
        status = ('success' if build['returncode'] == 0 else 'FAILED') if build else ('not requested' if row['checkout'].endswith('f') else 'not run')
        cp_test = bool(props['cp.test']) if 'cp.test' in props else 'not run'
        lines.append(f"| {row['checkout']} | {props.get('classes.modified', '').replace(chr(10), '<br>')} | {row.get('classes_match', 'pending')} | {status} | {build.get('wall_seconds', '—')} | {props.get('dir.src.classes', 'not run')} | {props.get('dir.src.tests', 'not run')} | {cp_test} |")
    lines += ['', 'Raw exports: `stage0-export/<checkout>.txt`. Full command logs: `stage0-logs/`.', '']
    if 'lang_test' in data:
        lines += ['## Lang-4b developer tests', '', '```json', json.dumps(data['lang_test'], indent=2), '```', '']
    for row in data['checkouts']:
        if row.get('compile', {}).get('returncode', 0):
            lines += [f"## {row['checkout']} compile error (last 40 stderr lines)", '', '```text', row['compile']['error_tail'], '```', '']
    if data.get('stop_reason'):
        lines += ['## Stop reason', '', data['stop_reason'], '']
    (RESULTS / 'stage0-d4j-checks.md').write_text('\n'.join(lines))


def main():
    LOGS.mkdir(parents=True, exist_ok=True)
    EXPORTS.mkdir(exist_ok=True)
    (ROOT / 'd4j').mkdir(exist_ok=True)
    records = json.loads((ROOT / 'bundle/Defects4J-dataset.json').read_text(encoding='utf-8', errors='ignore'))
    data = {'timestamp_utc': datetime.now(timezone.utc).isoformat(), 'checkouts': []}
    try:
        for version in VERSIONS:
            project, revision = version.split('-')
            checkout = ROOT / 'd4j' / version
            row = {'checkout': version, 'exports': {}}
            data['checkouts'].append(row)
            config_path = checkout / '.defects4j.config'
            if config_path.is_file():
                config = dict(line.split('=', 1) for line in config_path.read_text().splitlines() if '=' in line and not line.startswith('#'))
                if config.get('pid') != project or config.get('vid') != revision:
                    raise RuntimeError(f'Existing checkout identity mismatch: {version}: {config}')
                row['checkout_reused'] = True
                row['checkout_returncode'] = 0
                print(f'Reuse verified checkout {version}', flush=True)
            else:
                print(f'Checkout {version}', flush=True)
                result, elapsed = command(['defects4j', 'checkout', '-p', project, '-v', revision, '-w', str(checkout)], LOGS / f'{version}-checkout.txt')
                row['checkout_reused'] = False
                row['checkout_returncode'] = result.returncode
                row['checkout_wall_seconds'] = elapsed
                if result.returncode:
                    raise RuntimeError(f'Checkout failed: {version}; see its checkout log.')
            save(data)
        for row in data['checkouts']:
            version = row['checkout']
            project, revision = version.split('-')
            checkout = ROOT / 'd4j' / version
            expected = sorted(r['class'] for r in records if r['project_name'] == project and str(r['bug-id']) == revision[:-1])
            row['json_classes'] = expected
            raw = []
            for prop in PROPERTIES:
                print(f'Export {version} {prop}', flush=True)
                log = LOGS / f'{version}-export-{prop}.txt'
                result, _ = command(['defects4j', 'export', '-p', prop, '-w', str(checkout)], log)
                raw.append(log.read_text())
                (EXPORTS / f'{version}.txt').write_text('\n'.join(raw))
                if result.returncode:
                    raise RuntimeError(f'Export failed: {version} {prop}; see raw export.')
                row['exports'][prop] = result.stdout.strip()
                if prop == 'classes.modified':
                    actual = sorted(n.rsplit('.', 1)[-1] for n in result.stdout.split())
                    row['classes_match'] = actual == expected
                    if actual != expected:
                        raise RuntimeError(f'Modified class mismatch for {version}: export={actual}, JSON={expected}.')
                save(data)
        for row in data['checkouts']:
            version = row['checkout']
            if version.endswith('f'):
                continue
            print(f'Compile {version}', flush=True)
            args = ['bash', '-c', 'TIMEFORMAT="wall_seconds=%3R"; time defects4j compile -w "$1"', '_', str(ROOT / 'd4j' / version)]
            result, elapsed = command(args, LOGS / f'{version}-compile.txt')
            row['compile'] = {'returncode': result.returncode, 'wall_seconds': elapsed}
            if result.returncode:
                row['compile']['error_tail'] = '\n'.join(result.stderr.splitlines()[-40:])
                if project_name(version) not in {'Closure', 'Mockito'}:
                    raise RuntimeError(f'Unexpected compile failure for {version}.')
            save(data)
        print('Test Lang-4b', flush=True)
        checkout = ROOT / 'd4j/Lang-4b'
        trigger, _ = command(['defects4j', 'export', '-p', 'tests.trigger', '-w', str(checkout)], LOGS / 'Lang-4b-tests.trigger.txt')
        if trigger.returncode:
            raise RuntimeError('Could not obtain known Lang-4b triggering tests.')
        test, elapsed = command(['defects4j', 'test', '-w', str(checkout)], LOGS / 'Lang-4b-test.txt')
        failure_file = checkout / 'failing_tests'
        failure_text = failure_file.read_text() if failure_file.exists() else ''
        (RESULTS / 'stage0-Lang-4b-failing-tests.txt').write_text(failure_text)
        expected = sorted(trigger.stdout.split())
        actual = sorted(line.removeprefix('--- ').strip() for line in failure_text.splitlines() if line.startswith('--- '))
        data['lang_test'] = {'returncode': test.returncode, 'wall_seconds': elapsed, 'stdout': test.stdout, 'expected_triggering_tests': expected, 'actual_failing_tests': actual, 'failing_count': len(actual), 'matches_known_triggers': actual == expected}
        if test.returncode or actual != expected:
            raise RuntimeError('Lang-4b developer tests did not match the known triggering tests.')
    except Exception as exc:
        data['stop_reason'] = str(exc)
        raise
    finally:
        save(data)
    print('All Step 2 checks completed.', flush=True)


def project_name(version):
    return version.split('-')[0]


if __name__ == '__main__':
    main()
