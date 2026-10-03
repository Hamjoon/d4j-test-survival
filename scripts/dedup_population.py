"""Freeze the unique Step 5b population."""
from collections import Counter
from decimal import Decimal
from pathlib import Path
import time
from dedup import load, save, write_dedup
from extract_v2 import TECHS
from p2_population import developer_population


def main():
    started = time.monotonic()
    state = load('results/p2-generation-rounds.json')
    assert state['dedup_complete']
    rows, dedup_records = write_dedup(state)
    before = load('results/archive/step5-before-dedup/p2-dedup.json')
    old_state = load('results/archive/step5-before-dedup/p2-generation-rounds.json')
    policy = load('results/p2-approved-policy.json')
    records = load('results/lang-records.json')
    dev_summaries, dev_methods = developer_population(records, load('results/p2-dev-baseline.json'))
    file_map = {f['file']: f for f in state['files'] if f['file']}
    llm_methods = []
    for row in rows:
        if not row['passed_at_t'] or row['is_duplicate']:
            continue
        f = file_map[row['file']]
        llm_methods.append(dict(bug_id=f['bug_id'], cut_class=f['class'], population='llm',
            round=f['round'], technique=f['technique'], class_name=f['class_name'], test_class=f['fqcn'],
            method=row['method'], own_class=False, file=f['file'], source_sha256=f['extracted_sha256'],
            body_hash=row['body_hash'], is_duplicate=False, baseline_status='pass', baseline_run_file=f['run_file']))
    summaries = []
    for dev in dev_summaries:
        bug = dev['bug_id']
        d = next(r for r in dedup_records if r['bug_id'] == bug)
        rounds = [r for r in state['rounds'] if r['bug_id'] == bug]
        unique = Counter(m['technique'] for m in llm_methods if m['bug_id'] == bug)
        removed = Counter(m['technique'] for m in rows if m['bug_id'] == bug and m['is_duplicate'])
        last = rounds[-1]
        reached = d['L_r_unique'] >= dev['D_r']
        assert reached or last['round'] == 30
        summaries.append(dict(dev, **{k:v for k,v in d.items() if k != 'bug_id'},
            rounds_used=last['round'], target_reached=reached, stop_reason='unique target reached' if reached else '30-round cap',
            technique_counts={t:unique[t] for t in TECHS}, technique_duplicates_removed={t:removed[t] for t in TECHS},
            technique_raw_counts={t:unique[t]+removed[t] for t in TECHS},
            technique_shares={t:unique[t]/d['L_r_unique'] if d['L_r_unique'] else None for t in TECHS},
            calls_including_reused=sum(r['calls'] for r in rounds),new_calls=sum(r['new_calls'] for r in rounds),
            cumulative_cost_usd=last['cumulative_cost_usd'],cumulative_new_cost_usd=last['cumulative_new_cost_usd']))
    manifest = dict(records=summaries, methods=dev_methods+llm_methods, reporting_policy=policy,
        full_dev_2x2_excluded_records=[57],dev_own_2x2_empty_records=[r['bug_id'] for r in summaries if not r['D_r_own']])
    save('results/p2-population.json',manifest)
    table = ['| Record / CUT | D_r | D_r_own | L_r raw | Duplicates removed | L_r_unique | Rounds | Unique target reached | ZSL | FSL | CoT | ToT | GToT |',
             '|---|---:|---:|---:|---:|---:|---:|---|---:|---:|---:|---:|---:|']
    for r in summaries:
        shares=[f'{r["technique_counts"][t]} ({r["technique_shares"][t]:.1%})' for t in TECHS]
        table.append(f'| Lang-{r["bug_id"]} / {r["class"]} | {r["D_r"]} | {r["D_r_own"]} | {r["L_r"]} | '
            f'{r["duplicates_removed"]} | {r["L_r_unique"]} | {r["rounds_used"]} | {"yes" if r["target_reached"] else "no"} | '+' | '.join(shares)+' |')
    totals={k:sum(r[k] for r in summaries) for k in ['D_r','D_r_own','L_r','duplicates_removed','L_r_unique','methods_hashed']}
    notes = [f'Totals: dev {totals["D_r"]}; dev-own {totals["D_r_own"]}; LLM raw {totals["L_r"]}, '
        f'duplicates removed {totals["duplicates_removed"]}, unique {totals["L_r_unique"]}.', '',
        'Technique cells count unique passing methods and their share of the unique population. The full D_r is the generation target. '
        'dev-own filters the existing dev population; no additional developer runs or compilation are needed.', '',
        'Lang-6 and Lang-17 have no matching own test class. Lang-28 has one, but its sole method fails at t. '
        'These empty dev-own populations are N/A and excluded from their 2×2 comparison.', '',
        'Lang-57 developer survival is N/A at every time point: '+policy['lang57_developer_note']+'. '
        'Exclude Lang-57 from both 2×2 comparisons. Its LLM survival uses the unique population.', '',
        '139 LLM methods passed at the buggy version where every developer method fails. '
        'For Lang-57, removing 35 exact duplicates leaves 104 unique passing LLM methods.']
    text=['# Part 2 populations at t — Step 5b', '',
        'LLM methods enter survival only when they passed at t and are the first passing occurrence of their body hash within the record. '
        'Hashes use the original source inside method braces with all whitespace removed, including whitespace in comments and string literals. '
        'Comments otherwise remain. Method names, annotations and signatures are outside the hash. '
        'Ledger order is round, ZSL/FSL/CoT/ToT/GToT, source order. No test source is edited.', '', *table,'',*notes]
    Path('results/p2-population.md').write_text('\n'.join(text)+'\n')
    delta_rounds=state['rounds'][len(old_state['rounds']):]
    extra_cost=Decimal(str(state['reported_cost_usd']))-Decimal(str(old_state['reported_cost_usd']))
    elapsed=round(time.monotonic()-started,3)
    report=dict(complete=True, before_raw=sum(r['L_r'] for r in before['records']),
        before_duplicates=sum(r['duplicates_removed'] for r in before['records']),
        before_unique=sum(r['L_r_unique'] for r in before['records']),after=totals,
        additional_calls=sum(r['calls'] for r in delta_rounds),additional_cost_usd=float(extra_cost),
        generation_wall_seconds=state['dedup_wall_seconds'],population_wall_seconds=elapsed)
    save('results/p2-step5b.json',report)
    print(report)


if __name__=='__main__':
    main()
