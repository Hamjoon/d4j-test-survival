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
        '## Lang-57 observation and later aggregation', '', policy['lang57_observation_for_handovers_b_and_c'], '',
        'Lang-57 remains in the experiment and its LLM survival is computed normally. '
        'Exclude Lang-57 from class-level 2x2 counts and footnote the exclusion. '
        'The dev-own 2x2 also omits empty baselines (Lang-6, Lang-17, Lang-28 and Lang-57), reporting them as N/A rather than all-pass.', '',
        'Every Step 8 table reporting dev must show dev-own beside it: survival by time point, survival by days, failure kinds, '
        'class-level 2x2 and counts matched. Include the Lang-57 observation unchanged in handover-part2-c.', '']
    (OUT / 'p2-population.md').write_text('\n'.join(population_text))
    extra_files = [f for f in state['files'] if f['round'] > 1]
    new_rounds = [r for r in state['rounds'] if r['round'] > 1]
    anomalies = []
    for file in extra_files:
        identity = f'Lang-{file["bug_id"]} r{file["round"]} {file["technique"]}'
        metadata = load(Path(file['directory']) / 'run.json')
        if metadata['finish_reason'] != 'stop' or metadata['content_empty']:
            anomalies.append(f'- {identity}: finish_reason={metadata["finish_reason"]}, empty={metadata["content_empty"]}.')
        if not file['csr_v2']:
            anomalies.append(f'- {identity}: unstructured extraction; no source repairs or additional call.')
        elif not file['compile']['compile_ok']:
            anomalies.append(f'- {identity}: compile failure {file["compile"]["categories"]}; `{file["compile"]["stderr_file"]}`.')
        for method in file.get('methods', []):
            if method['status'] in {'timeout', 'not-run'}:
                anomalies.append(f'- {identity} `{method["method"]}`: {method["status"]}, launch {method["launch"]}.')
        cost_attempts = len(list(Path(file['directory']).glob('attempt-*/run.json')))
        if cost_attempts > 1:
            anomalies.append(f'- {identity}: {cost_attempts} HTTP attempts retained.')
    costs = ['| Record | Rounds | New completions | New HTTP attempts | New reported USD | Including reused r1 USD |',
             '|---|---:|---:|---:|---:|---:|']
    for r in summaries:
        if r['new_calls']:
            attempts = sum(x['http_attempts'] for x in new_rounds if x['bug_id'] == r['bug_id'])
            costs.append(f'| Lang-{r["bug_id"]} | {r["rounds_used"]} | {r["new_calls"]} | {attempts} | '
                         f'{r["cumulative_new_cost_usd"]:.9f} | {r["cumulative_cost_usd"]:.9f} |')
    providers = Counter(load(Path(f['directory']) / 'run.json')['provider'] for f in extra_files)
    missing_costs = sum(r['cost_missing_successes'] for r in state['rounds'])
    handover = ['# Part 2 handover B — Step 5 stop', '',
        'Steps 4–5 are complete under the approved Step 3 review decisions. Stop here. No survival runs (Step 6 onward) or push were performed.', '',
        '## Final populations', '', *table, '',
        f'Full developer population: {len(dev_methods)} methods; dev-own: {sum(m["own_class"] for m in dev_methods)}; '
        f'LLM: {len(llm_methods)}. {sum(r["target_reached"] for r in summaries)}/14 records meet the full developer target.', '',
        'Records already meeting D_r after round 1 received no additional calls. The five techniques were all generated and evaluated '
        'before each round’s stopping decision. No source normalization, test repair, package repair or method removal was applied.', '',
        '## Calls and cost', '', *costs, '',
        f'New completions: {sum(r["new_calls"] for r in new_rounds)}; new HTTP attempts: {sum(r["http_attempts"] for r in new_rounds)}. '
        f'Reused round-1 completions: 70. Total completions represented: {len(state["files"])}.', '',
        f'New reported usage cost: ${state["new_reported_cost_usd"]:.9f}. '
        f'Reported cost including reused round 1: ${state["reported_cost_usd"]:.9f}. '
        f'Successful attempts missing cost: {missing_costs}. '
        'These amounts exclude the Part 1 probe; reused calls are historical cost, not new spending.', '',
        'results/p2-rounds.csv records per-record cumulative passing counts and cost after every round. '
        'cumulative_cost_usd includes that record’s reused round 1; cumulative_new_cost_usd includes only its new rounds. '
        'Global cumulative fields follow CSV ledger order: reused round-1 records in bug-id order, then completed new rounds in execution order. '
        'They describe accounting order rather than the original round-1 call timestamps. Cost uses usage.cost once per HTTP attempt, '
        'with canonical successful-response copies excluded from double-counting.', '',
        f'Provider counts for new completions: {dict(providers)}. Default OpenRouter routing was retained.', '',
        '## Timing', '', f'Step 4 recorded process wall time: {state["wall_seconds"]} seconds. '
        'Each round has generation_seconds and evaluation_seconds in p2-rounds.csv. '
        'Concurrency was at most four API calls; source evaluation within each round was sequential.', '',
        'Step 5 report construction time is recorded in results/p2-population-timing.json. '
        'Editing and offline verification time are not represented as generation wall time.', '',
        '## Anomalies', '', *(anomalies or ['No new length finishes, unstructured responses, compilation failures, timeouts, not-run results or HTTP retries.']), '',
        'Ordinary assertion failures and exceptions remain in the per-file results and are excluded from the passing population. '
        'Round-1 anomalies remain documented in handover-part2-a.md.', '',
        '## Approved reporting changes for Steps 6–8', '',
        'The full D_r remains the round target. dev-own is the exact matching test class filter and needs no extra runs. '
        'D_r_own is shown above. Lang-6 and Lang-17 have no matching class. Lang-28 has a matching class but zero passing methods, '
        'so its dev-own survival is also N/A. No baseline is manufactured for these empty subsets.', '',
        'Keep population = dev in p2-survival-methods.csv and add own_class as a boolean; use it to derive dev-own. '
        'All Step 8 tables that report dev must also report dev-own (time points, days, failure kinds, class-level 2x2, counts matched). '
        'The Step 5 manifest supplies all passing developer rows with this flag.', '',
        f'Lang-57 stays in the experiment. Developer survival is N/A at every time point with note "{note}". '
        'LLM survival is computed normally. Exclude Lang-57 from class-level 2x2 counts and put that exclusion in a footnote; '
        'also footnote empty dev-own baselines for that subset’s 2x2.', '',
        policy['lang57_observation_for_handovers_b_and_c'], '',
        'Repeat the preceding observation in handover-part2-c without interpretation. '
        'The decisions are preserved in docs/part2-review-decisions.md and results/p2-approved-policy.json.', '',
        '## Reproducibility and verification', '',
        'All completed calls retain canonical request.json, raw-response.json, response.md, raw.java, usage.json and run.json, '
        'plus every attempt under attempt-N. Raw responses and test sources are immutable. '
        'The retry/resume checks used offline fixtures before any new API call. '
        'Successful call artifacts and completed evaluation artifacts are reused on restart; uncertain transport attempts stop without blind retry.', '',
        'See results/p2-validation-b.json for request-parameter/prompt checks, response and source hashes, population filtering, '
        'stopping-policy checks, cost reconciliation and the credential scan. The API key was supplied only by compose env_file.', '',
        '## Stop', '', 'Step 5 is the required review stop. Steps 6–9 have not begun.', '']
    Path('docs/handover-part2-b.md').write_text('\n'.join(handover))
    save(OUT / 'p2-population-timing.json', dict(wall_seconds=round(time.monotonic() - started, 3)))
    print('\n'.join(table))


if __name__ == '__main__':
    main()
