"""Prepare and date all 28 versions; resolve CUT presence at the original path."""
import csv
from datetime import datetime, timezone
import io
import json
from pathlib import Path
import subprocess
import time

PROPS = ['cp.test', 'dir.src.classes', 'dir.bin.classes', 'dir.src.tests', 'dir.bin.tests',
         'classes.modified', 'tests.relevant', 'tests.trigger']
OUT = Path('results')
LOG = OUT / 'p2-d4j-logs'
EXPORT = OUT / 'd4j-export'


def save(path, data):
    path.write_text(json.dumps(data, indent=2) + '\n')


def run(command, label):
    start = time.monotonic()
    result = subprocess.run(command, capture_output=True, text=True)
    elapsed = round(time.monotonic() - start, 3)
    (LOG / (label + '.out')).write_text(result.stdout)
    (LOG / (label + '.err')).write_text(result.stderr)
    save(LOG / (label + '.json'), dict(command=command, exit_status=result.returncode, seconds=elapsed))
    if result.returncode:
        raise RuntimeError(f'Stop: {label} exited {result.returncode}; see {LOG}')
    return result.stdout.strip(), elapsed


def date(value):
    return datetime.strptime(value, '%Y-%m-%d %H:%M:%S %z')


def main():
    start = time.monotonic()
    started = datetime.now(timezone.utc).isoformat()
    LOG.mkdir(exist_ok=True)
    records = json.loads((OUT / 'lang-records.json').read_text())
    ids = {r['bug_id'] for r in records}
    query, _ = run(['defects4j', 'query', '-p', 'Lang', '-q',
                    'bug.id,revision.id.buggy,revision.date.buggy,revision.id.fixed,revision.date.fixed'], 'query')
    versions = {}
    for row in csv.reader(io.StringIO(query)):
        if row and row[0].isdigit() and int(row[0]) in ids:
            bug, rb, db, rf, df = row
            for suffix, revision, timestamp in [('b', rb, db), ('f', rf, df)]:
                version = f'Lang-{bug}{suffix}'
                versions[version] = dict(id=version, bug_id=int(bug), kind='buggy' if suffix == 'b' else 'fixed',
                                         revision=revision, date=timestamp)
    assert len(versions) == 28, f'Expected 28 revisions, found {len(versions)}'
    for version, info in versions.items():
        checkout = Path('/work/d4j') / version
        config = checkout / '.defects4j.config'
        if config.exists():
            values = dict(line.split('=', 1) for line in config.read_text().splitlines() if '=' in line and not line.startswith('#'))
            assert values.get('pid') == 'Lang' and values.get('vid') == version.split('-')[1]
            info['checkout_reused'] = True
        else:
            _, info['checkout_seconds'] = run(['defects4j', 'checkout', '-p', 'Lang', '-v', version.split('-')[1], '-w', str(checkout)], version + '-checkout')
            info['checkout_reused'] = False
        _, info['compile_seconds'] = run(['defects4j', 'compile', '-w', str(checkout)], version + '-compile')
        info['compile_ok'] = True
        info['exports'] = {}
        for prop in PROPS:
            value, _ = run(['defects4j', 'export', '-p', prop, '-w', str(checkout)], version + '-' + prop)
            info['exports'][prop] = value
            (EXPORT / (version + '.' + prop)).write_text(value + '\n')
        print(f'{version}: compiled; exports saved', flush=True)
        save(OUT / 'p2-version-preparation.json', versions)
    ordered = sorted((v for v in versions.values() if v['kind'] == 'buggy'), key=lambda v: date(v['date']))
    timeline = []
    for record in sorted(records, key=lambda r: r['bug_id']):
        base = versions[f'Lang-{record["bug_id"]}b']
        later = [v for v in ordered if date(v['date']) > date(base['date'])]
        points = [versions[f'Lang-{record["bug_id"]}f'], *later]
        row = dict(bug_id=record['bug_id'], **{'class': record['class']}, package=record['package'],
                   t=base['id'], date_t=base['date'], no_later_dataset_bug=not later, timepoints=[])
        for index, point in enumerate(points, 1):
            relative = Path(point['exports']['dir.src.classes']) / record['package'].replace('.', '/') / (record['class'] + '.java')
            present = (Path('/work/d4j') / point['id'] / relative).is_file()
            gap = (date(point['date']) - date(base['date'])).total_seconds() / 86400
            row['timepoints'].append(dict(step=index, id=point['id'], date=point['date'], days_after_t=round(gap, 9),
                                           calendar_days_after_t=(date(point['date']).date() - date(base['date']).date()).days,
                                           status='present' if present else 'absent', source_path=str(relative)))
        row['first_absent'] = next((p['id'] for p in row['timepoints'] if p['status'] == 'absent'), None)
        timeline.append(row)
    result = dict(started_at=started, wall_seconds=round(time.monotonic() - start, 3),
                  date_semantics='Exact elapsed 24-hour days from buggy revision; fixed version kept as step 1 even when gap is nonzero.',
                  buggy_order=[v['id'] for v in ordered], versions=versions, records=timeline)
    save(OUT / 'p2-timeline.json', result)
    lines = ['# Part 2 timeline', '', 'Order of buggy revisions: ' + ' → '.join(result['buggy_order']), '',
             'Presence uses the original package path. Days are exact elapsed days rounded to six decimals here; '
             'JSON retains nine decimals and calendar-day gaps. The own fixed version is always step 1.', '',
             '| Record | Step | Time point | Revision date | Days after t | CUT |', '|---|---:|---|---|---:|---|']
    for row in timeline:
        for p in row['timepoints']:
            lines.append(f'| Lang-{row["bug_id"]} / {row["class"]} | {p["step"]} | {p["id"]} | {p["date"]} | {p["days_after_t"]:.6f} | {p["status"]} |')
    lines += ['', '## Package boundary', '']
    for row in timeline:
        if row['first_absent']:
            lines.append(f'- Lang-{row["bug_id"]} ({row["package"]}.{row["class"]}): first absent at {row["first_absent"]}.')
    lines += ['', '## No later dataset bug', '']
    lines += [f'- {r["t"]}: own fixed version remains available as step 1; no chronologically later dataset buggy revision.' for r in timeline if r['no_later_dataset_bug']]
    (OUT / 'p2-timeline.md').write_text('\n'.join(lines) + '\n')
    # Explicit range checks requested by the experiment, after preserving evidence.
    first_lang3 = next(v['id'] for v in ordered if v['bug_id'] not in {43, 54, 55, 57, 64})
    for row in timeline:
        assert row['timepoints'][0]['status'] == 'present', f'Own fixed CUT absent: {row["t"]}'
        if row['bug_id'] in {43, 54, 55, 57, 64}:
            assert row['first_absent'] == first_lang3, f'Stop: unexpected package boundary {row["t"]}'
    print('Timeline complete:', result['buggy_order'], flush=True)
    print('Lang 2.x boundary:', first_lang3, flush=True)


if __name__ == '__main__':
    main()
