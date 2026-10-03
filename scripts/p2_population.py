"""Step 5: freeze passing-method populations and the approved reporting subsets."""
from collections import Counter
import json
from pathlib import Path
import time

TECHS = ['ZSL', 'FSL', 'CoT', 'ToT', 'GToT']
OUT = Path('results')


def load(path):
    return json.loads(Path(path).read_text())


def save(path, value):
    Path(path).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')


def developer_population(records, baseline):
    by_id = {r['bug_id']: r for r in records}
    summaries, methods = [], []
    for row in baseline['records']:
        meta = by_id[row['bug_id']]
        expected = meta['package'] + '.' + meta['class'] + 'Test'
        matched = [c for c in row['classes'] if c['fqcn'] == expected]
        own = sum(c.get('counts', {}).get('pass', 0) for c in matched)
        summaries.append(dict(bug_id=row['bug_id'], **{'class': row['class']}, D_r=row['D_r'],
                              D_r_own=own, own_test_class=expected, own_test_class_present=bool(matched),
                              own_baseline_empty=own == 0))
        for cls in row['classes']:
            for method in cls.get('methods', []):
                if method['status'] == 'pass':
                    methods.append(dict(bug_id=row['bug_id'], cut_class=row['class'], population='dev',
                        round=None, technique=None, class_name=cls['fqcn'].rsplit('.', 1)[-1],
                        test_class=cls['fqcn'], method=method['method'], own_class=cls['fqcn'] == expected,
                        file=cls['file'], source_sha256=cls['sha256'], baseline_status='pass',
                        baseline_run_file=cls['run_file']))
    return summaries, methods


def main():
    started = time.monotonic()
    state = load(OUT / 'p2-generation-rounds.json')
    assert state['complete'], 'Step 4 must finish before the Step 5 population is frozen'
    policy = load(OUT / 'p2-approved-policy.json')
    records = load(OUT / 'lang-records.json')
    baseline = load(OUT / 'p2-dev-baseline.json')
    dev_summaries, dev_methods = developer_population(records, baseline)
    llm_methods = []
    for file in state['files']:
        for method in file.get('methods', []):
            if method['status'] == 'pass':
                llm_methods.append(dict(bug_id=file['bug_id'], cut_class=file['class'], population='llm',
                    round=file['round'], technique=file['technique'], class_name=file['class_name'],
                    test_class=file['fqcn'], method=method['method'], own_class=False,
                    file=file['file'], source_sha256=file['extracted_sha256'], baseline_status='pass',
                    baseline_run_file=file['run_file']))
    summaries = []
    for dev in dev_summaries:
        bug = dev['bug_id']
        rounds = [r for r in state['rounds'] if r['bug_id'] == bug]
        group = [m for m in llm_methods if m['bug_id'] == bug]
        by_technique = Counter(m['technique'] for m in group)
        last = rounds[-1]
        summaries.append(dict(dev, L_r=len(group), rounds_used=last['round'], target_reached=len(group) >= dev['D_r'],
            stop_reason='target reached' if len(group) >= dev['D_r'] else '30-round cap',
            technique_counts={t: by_technique[t] for t in TECHS},
            technique_shares={t: by_technique[t] / len(group) if group else None for t in TECHS},
            calls_including_reused=sum(r['calls'] for r in rounds), new_calls=sum(r['new_calls'] for r in rounds),
            cumulative_cost_usd=last['cumulative_cost_usd'], cumulative_new_cost_usd=last['cumulative_new_cost_usd']))
    manifest = dict(records=summaries, methods=dev_methods + llm_methods, reporting_policy=policy,
                    full_dev_2x2_excluded_records=[57],
                    dev_own_2x2_empty_records=[r['bug_id'] for r in summaries if r['D_r_own'] == 0])
    save(OUT / 'p2-population.json', manifest)
    save(OUT / 'p2-dev-own-baseline.json', dict(records=dev_summaries, extra_compilations=0, extra_runs=0))
    table = ['| Record / CUT | D_r (target) | D_r_own | L_r | Rounds | Reached full D_r | ZSL | FSL | CoT | ToT | GToT |',
             '|---|---:|---:|---:|---:|---|---:|---:|---:|---:|---:|']
    for r in summaries:
        shares = [f'{r["technique_counts"][t]} ({r["technique_shares"][t]:.1%})' if r['L_r'] else '0 (N/A)' for t in TECHS]
        own = str(r['D_r_own']) if r['D_r_own'] else '0 (N/A survival)'
        table.append(f'| {r["bug_id"]} / {r["class"]} | {r["D_r"]} | {own} | {r["L_r"]} | {r["rounds_used"]} | '
                     f'{"yes" if r["target_reached"] else "no"} | ' + ' | '.join(shares) + ' |')
    totals = Counter(m['technique'] for m in llm_methods)
    note = policy['lang57_developer_note']
    population_text = ['# Part 2 populations at t', '',
        'Only methods with status pass at t enter a population. LLM method identities include record, round, technique, '
        'test class and method; repeated method names across rounds remain distinct. Counts are not deduplicated across records. '
        'Technique cells show method counts and their share of that record’s final LLM population.', '', *table, '',
        f'Totals: full dev {len(dev_methods)}, dev-own {sum(m["own_class"] for m in dev_methods)}, '
        f'LLM {len(llm_methods)}. LLM technique counts: {dict(totals)}.', '',
        '## Developer subset', '',
        'dev-own matches exactly <CUT package>.<CUT simple name>Test within tests.relevant and filters the existing passing dev methods. '
        'The full D_r remains the generation target. No additional developer test compilation or execution was performed.', '',
        '- Lang-6 and Lang-17 have no matching test class: D_r_own = 0 and dev-own survival is N/A.',
        '- Lang-28 has the matching class, but its only test fails at t: D_r_own = 0 and dev-own survival is N/A. Full-dev reporting remains unchanged.',
        f'- Lang-57: D_r = D_r_own = 0; developer survival is N/A at every time point, with note "{note}".', '',
        'The machine-readable population manifest retains population = dev and an own_class boolean. '
        'Step 6 must carry own_class into p2-survival-methods.csv. dev-own is a filtered view, not duplicated CSV rows.', '',
        '## Lang-57 observation and later aggregation', '', policy['lang57_observation'], '',
        'Lang-57 remains in the experiment and its LLM survival is computed normally. '
        'Exclude Lang-57 from class-level 2x2 counts and footnote the exclusion. '
        'The dev-own 2x2 also omits empty baselines (Lang-6, Lang-17, Lang-28 and Lang-57), reporting them as N/A rather than all-pass.', '',
        'Every Step 8 table reporting dev must show dev-own beside it: survival by time point, survival by days, failure kinds, '
        'class-level 2x2 and counts matched.', '']
    (OUT / 'p2-population.md').write_text('\n'.join(population_text))
    save(OUT / 'p2-population-timing.json', dict(wall_seconds=round(time.monotonic() - started, 3)))
    print('\n'.join(table))


if __name__ == '__main__':
    main()
