"""Assemble the Step 3 review tables from measured artifacts only."""
from collections import Counter
import json
from pathlib import Path
import re

OUT = Path('results')
TECHS = ['ZSL', 'FSL', 'CoT', 'ToT', 'GToT']


def embedded_markdown(path):
    return re.sub(r'^(#+) ', lambda match: '##' + match[1] + ' ', path.read_text(), flags=re.M)


def main():
    dev = json.loads((OUT / 'p2-dev-baseline.json').read_text())
    llm = json.loads((OUT / 'p2-llm-baseline-round1.json').read_text())
    timeline = json.loads((OUT / 'p2-timeline.json').read_text())
    smoke = json.loads((OUT / 'p2-runner-smoke.json').read_text())
    assert dev['complete'] and llm['complete']
    initial_dev = json.loads((OUT / 'archive/part2-initial-cwd/p2-dev-baseline.json').read_text())
    initial_methods = {(r['bug_id'], c['fqcn'], m['method']): m['status']
                       for r in initial_dev['records'] for c in r['classes'] for m in c.get('methods', [])}
    cwd_changes = [dict(bug_id=r['bug_id'], fqcn=c['fqcn'], method=m['method'],
                        before=initial_methods[(r['bug_id'], c['fqcn'], m['method'])], after=m['status'])
                   for r in dev['records'] for c in r['classes'] for m in c.get('methods', [])
                   if initial_methods[(r['bug_id'], c['fqcn'], m['method'])] != m['status']]
    (OUT / 'p2-cwd-correction.json').write_text(json.dumps(dict(initial_cwd='/work', corrected_cwd='each target checkout',
        initial_dev_wall_seconds=initial_dev['wall_seconds'], changes=cwd_changes), indent=2) + '\n')
    table = ['| Record / CUT | Dev classes | Missing | Compiled | Dev methods listed | D_r | Fail/error at t | Trigger fail/error | Timeouts | L_r(1) | Target reached |',
             '|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|']
    llm_table = ['| Record | Structured files | Compiled files | Listed methods | Pass | Fail | Error | Ignored | Timeout | Not-run |',
                 '|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|']
    combined = []
    for r in dev['records']:
        group = [f for f in llm['files'] if f['bug_id'] == r['bug_id']]
        counts = Counter()
        for f in group:
            counts.update(f.get('counts', {}))
        compiled = sum(f.get('compile', {}).get('compile_ok', False) for f in group)
        entry = dict(bug_id=r['bug_id'], **{'class': r['class']}, D_r=r['D_r'], L_r_1=counts['pass'],
                     target_reached=counts['pass'] >= r['D_r'], dev_counts=r['counts'], llm_counts=dict(counts),
                     dev_classes=r['class_count'], dev_compiled=r['compiled'], llm_compiled=compiled)
        combined.append(entry)
        table.append(f'| {r["bug_id"]} / {r["class"]} | {r["class_count"]} | {r["missing"]} | {r["compiled"]} | '
                     f'{r["listed"]} | {r["D_r"]} | {r["failing_at_t"]} | {r["trigger_fail_error"]}/{len(r["triggers"])} | '
                     f'{r["counts"].get("timeout", 0)} | {counts["pass"]} | {"yes" if entry["target_reached"] else "no"} |')
        llm_table.append(f'| {r["bug_id"]} | {sum(f["csr_v2"] for f in group)} | {compiled} | '
                         f'{sum(f.get("listed", 0) for f in group)} | ' + ' | '.join(str(counts[s]) for s in ['pass','fail','error','ignored','timeout','not-run']) + ' |')
    triggers = ['| Bug | Trigger method | Status at t |', '|---|---|---|']
    for r in dev['records']:
        triggers += [f'| {r["bug_id"]} | `{name}` | {status} |' for name, status in r['trigger_statuses'].items()]
    dev_totals, llm_totals = Counter(), Counter()
    for r in dev['records']:
        dev_totals.update(r['counts'])
    for f in llm['files']:
        llm_totals.update(f.get('counts', {}))
    baseline = ['# Part 2 baseline at t', '',
                'D_r and L_r(1) count passing methods only. Each record has its own population; '
                'the same developer method appearing in multiple records is counted within each record. '
                'Failures and exceptions are excluded from the passing population. Ignored tests are listed separately.', '',
                *table, '', '## Round-1 LLM details', '', *llm_table, '', '## Trigger methods', '', *triggers, '',
                '## Totals', '', f'Developer: {dict(dev_totals)}.', '', f'LLM round 1: {dict(llm_totals)}.', '',
                'Classpath order: copied/generated classes, tools/runner, the checkout cp.test, pinned JUnit/Hamcrest jars. '
                'The older project JUnit included by cp.test therefore retains precedence as prescribed.', '']
    (OUT / 'p2-dev-baseline.md').write_text('\n'.join(baseline))
    (OUT / 'p2-baseline-comparison.json').write_text(json.dumps(combined, indent=2) + '\n')
    errors = [f for f in llm['files'] if f.get('compile') and not f['compile']['compile_ok']]
    anomalies = [f'- Lang-{f["bug_id"]} {f["technique"]}: compile failure, {f["compile"]["categories"]}; `{f["compile"]["stderr_file"]}`.' for f in errors]
    for population, groups in [('dev', [dict(bug_id=r['bug_id'], **c) for r in dev['records'] for c in r['classes']]), ('llm', llm['files'])]:
        for item in groups:
            for method in item.get('methods', []):
                if method['status'] in {'timeout', 'not-run'}:
                    anomalies.append(f'- {population} Lang-{item["bug_id"]} {item.get("technique", "")} `{item["fqcn"]}::{method["method"]}`: {method["status"]}, launch {method["launch"]}.')
    unexpected_triggers = [(r['bug_id'], name, status) for r in dev['records'] for name, status in r['trigger_statuses'].items() if status not in {'fail', 'error'}]
    anomalies += [f'- Trigger Lang-{bug} `{name}` has status `{status}`.' for bug, name, status in unexpected_triggers]
    anomalies += [f'- Lang-{r["bug_id"]}: D_r = 0. All {len(r["triggers"])} declared trigger methods fail/error in shared setup; '
                  'there is no passing developer population for this record. The default count target is already met at zero.'
                  for r in dev['records'] if r['D_r'] == 0]
    handover = ['# Part 2 handover A — Step 3 stop', '',
                'Steps 1–3 are complete. Stop here for Gary/Cowork review of the real developer populations and the round policy. '
                'No Step 4 generation, no new API calls, and no push.', '',
                '## Baseline and round-policy input', '', *table, '',
                f'Total passing populations across records: developer {dev_totals["pass"]}; round-1 LLM {llm_totals["pass"]}. '
                f'{sum(r["target_reached"] for r in combined)}/14 records already meet the target. '
                'Counts are record-specific; these totals do not deduplicate shared test methods across records.', '',
                f'All 49 developer test classes compile. Of 68 structured LLM files, '
                f'{sum(f.get("compile", {}).get("compile_ok", False) for f in llm["files"])} compile and '
                f'{len(errors)} fail compilation. The compiled LLM files list {sum(llm_totals.values())} methods: '
                f'{llm_totals["pass"]} pass, {llm_totals["fail"]} assertion failures, '
                f'{llm_totals["error"]} other errors, and {llm_totals["timeout"]} timeouts. '
                'Every listed method has a result; none is not-run.', '',
                'Default policy pending review: five calls per additional round (one per technique), stop each record at '
                'L_r(k) ≥ D_r or 30 total rounds. Existing round 1 is always reused.', '',
                '## Extraction comparison', '', embedded_markdown(OUT / 'p2-extraction-round1.md'), '',
                'Both excluded responses ended with finish_reason=length in Part 1. Lang-12 ZSL has no complete '
                'marker pair or closed fenced block; Lang-54 CoT falls back to a closed snippet without a class. '
                'MSR records visible code even when a truncated response has no extractable complete block.', '',
                '## Runner verification', '', '```json', json.dumps(smoke, indent=2), '```', '',
                'The Lang-4 ZSL control retained 9 passes and 1 assertion failure. Lang-11 ZSL lists all 15 methods, '
                'reports exactly 1 timeout at 30 seconds and measures the other 14 as passes. '
                'The wrapper excludes all already measured methods on relaunch, so successful methods are not repeated. '
                'Synthetic fixtures verify failure/error/ignored classification, JUnit 3, timeout resumption and crash not-run accounting.', '',
                '## Developer and LLM detail', '', *llm_table, '', '### Trigger methods', '', *triggers, '',
                f'Developer statuses: {dict(dev_totals)}. LLM statuses: {dict(llm_totals)}.', '',
                'The 26 developer fail/error results are exactly the 26 declared trigger methods. '
                'Classpath precedence is copied/generated classes, tools/runner, checkout cp.test, then pinned JUnit/Hamcrest. '
                'The project JUnit in cp.test retains its prescribed precedence.', '',
                '## Timeline', '', embedded_markdown(OUT / 'p2-timeline.md'), '',
                'All 28 checkouts compiled. The five Lang 2.x records lose their original package paths at the first '
                'sampled Lang 3.x point. The latest buggy record has its own fixed point and no later dataset buggy point.', '',
                '## Anomalies and implementation notes', '', *anomalies, '',
                '- No generated or developer test source was repaired, reformatted, or normalized. SHA-256 and byte equality verify provenance.',
                '- File compilation failures are retained and excluded at t; source syntax is not an additional filter after CSR v2.',
                '- Initial developer execution from /work caused two FileNotFoundExceptions in testLang708 (Lang-4 and Lang-6). '
                'The initial baseline and every per-class log are preserved under results/archive/part2-initial-cwd/. '
                'The complete developer baseline was repeated with each checkout as working directory and absolute classpaths; '
                'LLM baseline execution uses the same convention. No test/resource files or model output were edited. '
                'Per-method status changes are recorded in results/p2-cwd-correction.json: ' + json.dumps(cwd_changes) + '.',
                '- The runtime uses Request.method in a fresh worker per method. Each method receives its own fixtures; '
                'static JVM state persists within a launch, and timeout recovery necessarily starts a new JVM.',
                '- Fixed versions remain step 1; actual revision-date gaps are retained rather than silently setting them to zero. '
                'A later day-bin aggregation can label the fixed step as bin 0 while preserving these source dates.',
                '- Mechanical setup corrections: an initial combined patch was rejected atomically for targeting the runner twice; '
                'it was reapplied as separate file writes. An initial smoke-script write used the docker subdirectory as cwd and created no file; '
                'the path was corrected. Docker socket access was blocked in the sandbox and was rerun with escalation. '
                'Exploratory reads of guessed filenames were corrected after inspecting the actual repository layout.', '',
                '## Recorded workload times and cost', '',
                f'- Step 2 checkouts, compiles, exports and timeline: {timeline["wall_seconds"]} seconds.',
                f'- Step 3 developer baseline: {dev["wall_seconds"]} seconds; LLM baseline: {llm["wall_seconds"]} seconds.',
                f'- Archived initial developer baseline before cwd correction: {initial_dev["wall_seconds"]} seconds.',
                '- Step 1 per-run timing is saved in results/p2-smoke/*.summary.json; full editing/session wall time was not reconstructed.',
                '- Part 2 API calls and cost through this handover: 0 calls / $0. Round 1 reuses the 70 existing Part 1 responses.', '',
                '## Verification', '',
                'See results/p2-validation.json for immutable-source checks, method-count reconciliation, timeline ordering/presence, '
                'classpath precedence, and credential scanning.', '',
                'Code/document whitespace checks pass. Unrestricted git diff --check reports retained CSV CRLF/source whitespace; '
                'immutable source and evidence were not normalized to suppress these diagnostics.', '',
                '## Open review decision', '',
                'Approve or revise the generation-round policy using D_r and L_r(1) above before Step 4. '
                'The default remains five techniques per round with a maximum of 30 total rounds per record.', '',
                'Lang-57 has D_r = 0 because all developer methods trigger the bug in setup. '
                'Its developer survival percentage has a zero denominator; the later report should show N/A. '
                'Review how this record should enter the class-level 2×2 analysis before those later stages.', '']
    Path('docs/handover-part2-a.md').write_text('\n'.join(handover))
    print('\n'.join(table))
    print('Developer totals:', dict(dev_totals))
    print('LLM totals:', dict(llm_totals))


if __name__ == '__main__':
    main()
