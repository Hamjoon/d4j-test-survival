"""Step 9: self-contained handover with every Step 8 table and workload provenance."""
from pathlib import Path
import time
from aggregate import table
from generate_rounds import load, save


def main():
    started=time.monotonic()
    state=load('results/p2-generation-rounds.json');matrix=load('results/p2-survival-matrix.json')
    classification=load('results/p2-fixed-classification.json');timeline=load('results/p2-timeline.json')
    survival=load('results/p2-survival-progress.json');step5b=load('results/p2-step5b.json')
    population=load('results/p2-population.json');policy=load('results/p2-approved-policy.json')
    assert matrix['complete'] and classification['complete'] and policy['step7_patch_lines']=='actual fixed-version lines'
    calls=sum(r['calls'] for r in state['rounds']);new_calls=sum(r['new_calls'] for r in state['rounds'])
    smoke=sum(load(p)['wall_seconds'] for p in Path('results/p2-smoke').glob('*.summary.json'))
    initial_dev=load('results/archive/part2-initial-cwd/p2-dev-baseline.json')['wall_seconds']
    current_dev=load('results/p2-dev-baseline.json')['wall_seconds'];llm_baseline=load('results/p2-llm-baseline-round1.json')['wall_seconds']
    aggregate_timing=load('results/p2-aggregation-timing.json')
    out=['# Part 2 handover C — Step 9 stop','',
        'Steps 7–9 are complete under the approved review decisions. The final artifacts are committed locally with this handover; stop here without pushing. '
        'The final LLM population is 1,509 raw passing methods minus 156 exact duplicates = 1,353 unique methods. All 14 records meet the full developer target on unique counts.','',
        'At the fixed versions, dev passes 703/703, dev-own passes 95/95, and LLM passes 1,331/1,353 (98.37%). '
        'The 22 unique LLM nonpasses (21 assertion failures and one error) all intersect changed fixed-version lines and are classified as patch-related under the specified coverage rule. '
        'One of those methods, Lang-55’s timing test, passes in its isolated coverage rerun; its original survival outcome is retained. '
        'The classification does not establish that the patch caused a failure.','',
        '## Approved patch direction','',
        '`step7_patch_lines = "actual fixed-version lines"` is recorded in docs/part2-review-decisions.md and results/p2-approved-policy.json. '
        'The original document assumed buggy-to-fixed orientation; the Defects4J patches are fixed-to-buggy. '
        'Each patch was reversed, its old side verified against Lang-<B>b, and its new side verified against Lang-<B>f. '
        'Changed + lines in the reversed patch supply the fixed-version patched-line set; a deletion-only hunk maps to its position line. '
        'No checkout or test source was edited.','',
        'The 14 original patches, 14 reversed patches, original audit and reversed audit are retained in results/p2-patch-audit/. '
        'results/p2-fixed-patched-lines.json records the actual sets. Each classified method retains its isolated runner log, JaCoCo execution file, XML report, '
        'executed lines and intersection in its survival/.../classification/ directory.','',
        '## Calls, costs and count targets','',
        f'The generation ledger represents {calls} calls: 70 reused Part 1 calls and {new_calls} new Part 2 calls. '
        f'Total reported generation cost is ${state["reported_cost_usd"]:.9f}; new Part 2 cost is ${state["new_reported_cost_usd"]:.9f}. '
        f'Step 5b accounts for {step5b["additional_calls"]} of those new calls and ${step5b["additional_cost_usd"]:.9f}. '
        'Steps 6–9 made no generation API calls. These figures exclude the separate Part 1 model probe; reused calls are historical cost, not new spending.','',
        *table(['Record','Rounds','Calls incl. reused r1','New calls','New reported USD','Total reported USD'],
        [[f'Lang-{r["bug_id"]}',r['rounds_used'],r['calls_including_reused'],r['new_calls'],
          f'{r["cumulative_new_cost_usd"]:.9f}',f'{r["cumulative_cost_usd"]:.9f}'] for r in population['records']]),'',
        'Costs come from usage.cost, counted once per HTTP attempt; canonical response copies are not counted again. '
        'All 60 new calls succeeded on their first HTTP attempt. Each full round has five techniques and preserves the original rendered prompt, '
        'temperature 0.7, max_tokens 4096 and single user message. Concurrency never exceeds four. '
        'results/p2-rounds.csv contains raw and unique cumulative counts and cumulative cost after every round.','',
        'Exact-body deduplication hashed all 2,020 test methods in all 127 structured files, including nonpasses and compile failures. '
        'Before Step 5b, 1,466 raw passes contained 147 duplicates and 1,319 unique methods. '
        'Only Lang-13 fell short after deduplication (196 unique versus D_r = 207); round 6 raised it to 230 unique methods. '
        'The final counts are 1,509 raw, 156 removed, 1,353 unique. Source bodies were never edited.','',
        '## Recorded wall time by step','',
        'These are recorded process wall times, not a reconstruction of editing, review waits or the entire terminal session. '
        'Interrupted/repeated measured work is identified below. Step 1 has only per-run smoke timing; its full preparation time was not recorded.','',
        *table(['Step','Recorded seconds','Scope'],[
            ['1',f'{smoke:.3f}','Sum of five saved runner smoke runs; extraction, compilation and editing time not recorded'],
            ['2',f'{timeline["wall_seconds"]:.3f}','Checkout/compile/export/timeline preparation'],
            ['3',f'{current_dev+llm_baseline+initial_dev:.3f}',f'Final dev {current_dev:.3f} + round-1 LLM {llm_baseline:.3f} + archived initial dev {initial_dev:.3f}'],
            ['4',f'{state["wall_seconds"]:.3f}','Approved raw-target generation rounds and baseline evaluation'],
            ['5',f'{load("results/p2-population-timing.json")["wall_seconds"]:.3f}','Original population report construction'],
            ['5b',f'{step5b["generation_wall_seconds"]+step5b["population_wall_seconds"]:.3f}',f'Extra generation/evaluation/dedup {step5b["generation_wall_seconds"]:.3f} + population report {step5b["population_wall_seconds"]:.3f}; offline audits excluded'],
            ['6',f'{survival["wall_seconds"]:.3f}','Sum of interrupted and resumed survival process sessions, including corrected affected runs'],
            ['7',f'{classification["wall_seconds"]:.3f}','Patch reversal/verification and 22 isolated coverage classifications'],
            ['8',f'{sum(r["wall_seconds"] for r in aggregate_timing["runs"]):.3f}','Initial aggregation and final aggregation including coverage-rerun caveat'],
            ['9','HANDOVER_SECONDS_PLACEHOLDER','Handover construction through first file write; separate final audit timing is in results/p2-validation-c.json']]),'',
        'The raw ledger preserves generation/evaluation time per round. Each survival file and coverage run also retains individual command timings.','',
        '## Deviations, corrections and observations','',
        '1. **Patch orientation (approved Step 7 correction):** the original document assumed buggy-to-fixed orientation; Defects4J patches are fixed-to-buggy. '
        'Reverse patches and use actual fixed-version + lines, verifying both directions. The contradiction caused a review stop; the user approved the correction on 2026-10-03.',
        '2. **Unique LLM population (approved Step 5b change):** hash original text inside each method’s braces after removing every whitespace character, '
        'including whitespace within comments and literals. Names, signatures and annotations are outside the hash. '
        'Keep the first passing occurrence per record in round/technique/source order; a nonpassing occurrence cannot displace a later pass. '
        'The count target now uses unique passing methods. Duplicates stay in unchanged source files and are excluded through runner selection.',
        '3. **Dev-own (approved Step 3 review change):** add the exact <CUT package>.<CUT simple name>Test subset as a filter over existing dev rows. '
        'No additional developer compilation or run is performed for this population. Lang-6 and Lang-17 lack the matching class. '
        'Lang-28’s matching class has zero passing baseline methods. Empty subsets are N/A and excluded from their 2×2 comparison.',
        '4. **Lang-57 (approved Step 3 review change):** retain the record, report developer survival N/A, and omit it from both class-level 2×2 comparisons. '
        'LLM reporting continues normally. The exact developer note appears below and in the tables.',
        '5. **Baseline working directory correction:** the initial developer run from /work produced two resource FileNotFoundExceptions in testLang708 '
        '(Lang-4 and Lang-6). All developer baselines were rerun from their checkout directories; those two statuses changed to pass. '
        'Initial evidence remains in results/archive/part2-initial-cwd/. Survival and coverage runs use the target checkout cwd and absolute runtime classpaths.',
        '6. **Inherited-method runner correction:** the initial Step 6 wrapper treated a missing inherited method as a class-wide listing failure. '
        'It now executes every present selected method and records only the missing methods as not-run (JUnitMethodMissing). '
        'Six affected artifacts are archived under results/archive/step6-inherited-listing/. '
        'At each of the three affected Lang-13 points, 26 not-run results became two not-run plus 24 passes; the 28 compile-fail methods remained unchanged.',
        '7. **Coverage rerun outcome:** Lang-55 r1 FSL testMultipleStartStopCyclesWithReset failed in Step 6 at Lang-55f but passed during isolated JaCoCo execution. '
        'It uses Thread.sleep(3), Thread.sleep(4), duration lower bounds and second != first. The evidence does not identify which assertion failed or why the result changed. '
        'Its executed lines include patched line 118, so the specified rule still labels it patch-related. The Step 6 outcome is retained in survival and 2×2 counts; no extra retry policy was introduced.',
        '8. **Operational reporting choices:** absent CUTs contribute no survival denominator and no 2×2 pair. '
        'Every present status other than pass is a class-level nonpass, including compile-fail and not-run. '
        'Fixed versions are day bin 0; later versions retain the timeline’s exact elapsed 24-hour day gaps (nine decimal places). '
        'Coverage lines are for the CUT source file, including its nested compiled classes. The original fixed revision-date gaps remain in the timepoint tables.',
        '9. **Mechanical command corrections:** earlier wrong working-directory writes and an atomically rejected patch were corrected before execution, as recorded in handover A. '
        'A host validation import lacked requests, so validation ran in the prescribed container. An inline patch-check quoting error was replaced by scripts/verify_selection.py. '
        'Docker socket access in earlier turns used the required escalation. These corrections did not alter the experiment parameters or test sources.', '',
        'Compile failures and unstructured model responses were retained without repair. Per-file diagnostics and the earlier handovers document their details. '
        'No generated or developer source file was edited, no package normalization was applied, and no production checkout source was changed. '
        'The API credential was supplied only by docker/.env through Compose and was checked without printing its value. '
        'Whitespace checks cover code and documentation; original source, patch and evidence whitespace is retained unchanged.','',
        '## Absent time points and package boundary','',
        *table(['Record','Original CUT','First absent point','Absent points','Last sampled point'],
        [[f'Lang-{r["bug_id"]}',r['package']+'.'+r['class'],
          next(p['id'] for p in r['timepoints'] if p['status']=='absent'),sum(p['status']=='absent' for p in r['timepoints']),r['timepoints'][-1]['id']]
         for r in timeline['records'] if any(p['status']=='absent' for p in r['timepoints'])]),'',
        'The five Lang 2.x records lose their original package path at Lang-28b, the first sampled Lang 3.x point. '
        'There are 45 absent pairs and 60 present pairs among 105 record/timepoint pairs. '
        'Lang-4 has its own fixed point and no chronologically later dataset buggy revision.','',
        '## Lang-57 observation','',
        '139 LLM methods passed at the buggy version where every developer method fails.','',
        'Lang-57 has 139 raw passing LLM methods, 35 exact duplicates removed and 104 unique passing methods. '
        'All 104 pass at each of its four present time points. Developer survival is N/A at every time point: '
        '**all developer methods are trigger tests; no developer baseline at t**.','',
        '## Validation and artifact map','',
        'results/p2-validation-c.json records the final independent audit: both patch directions checked with git apply --check, '
        'changed-line mapping and deletion-only position check, exact classification candidate set, single-method execution, '
        'XML coverage/intersection reconciliation, all aggregation views recomputed from method rows, both 2×2 tables, '
        'source hashes, unchanged frozen Step 6 artifacts, Markdown table structure, and a non-disclosing credential scan. '
        'The earlier results/p2-survival-validation.json verifies all 12,803 rows and zero duplicate method executions.', '',
        '- Method outcomes: results/p2-survival-methods.csv; file compilation/execution evidence: results/p2-survival-files.json and survival/.',
        '- Fixed classification: results/p2-fixed-classification.csv and .json; patch evidence: results/p2-patch-audit/.',
        '- Full aggregation: results/p2-survival-summary.md and results/p2-survival-matrix.json, with matching numbers.',
        '- Frozen population and duplicates: results/p2-population.json, results/p2-population.md and results/p2-dedup.csv.',
        '- Generation and costs: results/p2-rounds.csv, results/p2-generation-rounds.json and runs/lang/.',
        '- Earlier reviews: docs/handover-part2-a.md, docs/handover-part2-b.md, docs/handover-part2-b-addendum.md and docs/part2-review-decisions.md. '
        'docs/part2-step7-review.md is the historical review stop resolved by the current decision.', '',
        '## Open questions and limits','',
        '- The Lang-55 isolated rerun differs from its saved survival outcome. A repeatability study could investigate this later; no additional repetitions were performed.',
        '- The coverage intersection rule does not establish causation. In particular, common constructor or setup lines can intersect a patch, and a classified method can pass when rerun in isolation.',
        '- Deduplication detects only the specified whitespace-stripped body equality. It does not collapse semantically equivalent tests, rename local variables, or compare across records.',
        '- Counts meet or exceed full D_r rather than forming an exactly size-matched sample. Full dev, dev-own and LLM populations test different scopes. '
        'Pooled method/timepoint results reuse records and methods across versions; the report does not claim independent observations or a causal population comparison.',
        '- The experiment keeps original package paths, so Lang 2.x populations become absent after the Lang 3.x boundary. There is no package-migration experiment.',
        '- No execution decision remains pending for this authorized run. Further generation, repetitions or alternative analyses require a new task.', '',
        '## Complete Step 8 tables','',
        'The complete generated summary follows verbatim so this handover contains every requested table.','',
        Path('results/p2-survival-summary.md').read_text()]
    path=Path('docs/handover-part2-c.md')
    text='\n'.join(out).rstrip()+'\n'
    path.write_text(text)
    elapsed=round(time.monotonic()-started,3)
    path.write_text(text.replace('HANDOVER_SECONDS_PLACEHOLDER',f'{elapsed:.3f}'))
    save('results/p2-handover-c-timing.json',dict(complete=True,wall_seconds=elapsed,
        scope='handover construction through first file write; timing substitution, editing, validation and commit excluded'))
    print(dict(handover='docs/handover-part2-c.md',calls=calls,new_calls=new_calls,cost=state['reported_cost_usd'],wall_seconds=elapsed))


if __name__=='__main__':main()
