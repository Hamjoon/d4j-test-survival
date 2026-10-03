"""Run JUnit methods; resume after timeouts without rerunning measured methods."""
import argparse
from collections import Counter
import json
from pathlib import Path
import subprocess
import time


def invoke(command, timeout, cwd=None):
    start = time.monotonic()
    try:
        result = subprocess.run(command, capture_output=True, text=True, errors='replace', timeout=timeout, cwd=cwd)
        out, err, code = result.stdout, result.stderr, result.returncode
    except subprocess.TimeoutExpired as exc:
        def decode(value):
            return value.decode('utf-8', errors='replace') if isinstance(value, bytes) else (value or '')
        out, err, code = decode(exc.stdout), decode(exc.stderr) + '\nWrapper process timeout', 124
    return dict(command=command, exit_status=code, stdout=out, stderr=err,
                wall_seconds=round(time.monotonic() - start, 3))


def events(text):
    result = []
    for line in text.splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        if isinstance(event, dict) and isinstance(event.get('method'), str):
            result.append(event)
    return result


def run_class(fqcn, classpath, output, timeout_ms=30000, java_options=(), working_directory=None, include_methods=None):
    start = time.monotonic()
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    cwd = str(Path(working_directory).resolve()) if working_directory is not None else str(Path.cwd())
    if working_directory is not None:
        classpath = ':'.join(str(Path(entry).resolve()) for entry in classpath.split(':'))
    base = ['java', *java_options, '-cp', classpath, 'JsonRunner', fqcn]
    listing = invoke(base + ['--list'], 60, cwd)
    all_methods = [event['method'] for event in events(listing['stdout'])]
    wanted = None if include_methods is None else set(include_methods)
    methods = all_methods if wanted is None else [name for name in all_methods if name in wanted]
    excluded = [name for name in all_methods if wanted is not None and name not in wanted]
    missing = [] if wanted is None else sorted(wanted - set(all_methods))
    if len(all_methods) != len(set(all_methods)):
        raise RuntimeError('Duplicate JUnit method identities: ' + fqcn)
    completed, launches, list_error = {}, [], ''
    if listing['exit_status'] or not all_methods:
        list_error = f'Listing failed: exit={listing["exit_status"]}; methods={len(all_methods)}; missing={missing}; ' + listing['stderr'][-4000:]
    else:
        while len(completed) < len(methods):
            launch = len(launches) + 1
            command = base + ['--timeout-ms', str(timeout_ms)]
            if excluded or completed:
                command += ['--exclude', ','.join([*excluded, *completed])]
            remaining = len(methods) - len(completed)
            result = invoke(command, remaining * (timeout_ms / 1000 + 5) + 60, cwd)
            result['launch'] = launch
            launches.append(result)
            current = []
            for event in events(result['stdout']):
                name = event['method']
                if name not in methods or name in completed:
                    raise RuntimeError(f'Unexpected/repeated method {name} in {fqcn}')
                if event.get('status') not in {'pass', 'fail', 'error', 'ignored', 'timeout'}:
                    raise RuntimeError(f'Invalid method status: {event}')
                current.append(event)
                completed[name] = {**event, 'launch': launch}
            if result['exit_status'] == 3 and any(e['status'] == 'timeout' for e in current):
                continue
            if len(completed) < len(methods):
                tail = result['stderr'][-4000:]
                for name in methods:
                    if name not in completed:
                        completed[name] = dict(method=name, status='not-run', exception=None,
                                               message=f'JVM exited {result["exit_status"]}; stderr tail: {tail}',
                                               milliseconds=None, launch=launch)
            break
    if missing and not list_error:
        for name in missing:
            completed[name] = dict(method=name, status='not-run', exception='JUnitMethodMissing',
                message='Baseline method absent from JUnit listing at target version (possibly inherited)',
                milliseconds=None, launch=0)
        methods += missing
    if list_error and wanted is not None:
        methods = sorted(wanted)
        completed = {name: dict(method=name, status='not-run', exception='JUnitListingFailure',
            message=list_error, milliseconds=None, launch=0) for name in methods}
    measured = [completed[name] for name in methods if name in completed]
    output.write_text(''.join(json.dumps(row, ensure_ascii=False) + '\n' for row in measured))
    summary = dict(fqcn=fqcn, classpath=classpath, cwd=cwd, timeout_ms=timeout_ms, listed_methods=methods,
                   all_listed_methods=all_methods, selected_methods=None if wanted is None else sorted(wanted),
                   excluded_methods=excluded, missing_methods=missing, listing=listing, list_error=list_error, launches=launches,
                   methods=measured, counts=dict(Counter(row['status'] for row in measured)),
                   wall_seconds=round(time.monotonic() - start, 3))
    output.with_suffix('.summary.json').write_text(json.dumps(summary, indent=2, ensure_ascii=False) + '\n')
    if list_error and wanted is None:
        raise RuntimeError(list_error)
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('fqcn')
    parser.add_argument('--classpath', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--timeout-ms', type=int, default=30000)
    parser.add_argument('--cwd', help='Checkout working directory for relative test resources')
    parser.add_argument('--include', help='Comma-separated method names; all other listed methods are excluded')
    args = parser.parse_args()
    summary = run_class(args.fqcn, args.classpath, args.output, args.timeout_ms, working_directory=args.cwd,
                        include_methods=None if args.include is None else args.include.split(','))
    print(json.dumps(dict(fqcn=args.fqcn, counts=summary['counts'], launches=len(summary['launches']))))
