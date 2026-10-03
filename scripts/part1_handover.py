"""Assemble the Part 1 first-stop handover from saved evidence."""
import json
from pathlib import Path

def main():
    env = json.loads(Path('results/env.json').read_text())
    records = json.loads(Path('results/lang-records.json').read_text())
    manifest = json.loads(Path('prompts/manifest.json').read_text())
    probe = Path('results/model-probe.md').read_text()
    selection_path = Path('results/model-selection.json')
    completed = selection_path.exists()
    selection = json.loads(selection_path.read_text()) if completed else None
    preflight_path = Path('results/probe-preflight.json')
    check = json.loads(preflight_path.read_text()) if preflight_path.exists() else {}
    key_present = completed or check.get('container_key_present', False)
    models_blocked = not completed and key_present and check.get('model_list_status') == 200
    summary = ('Steps 1–6 completed. Stop for Gary’s confirmation before Step 7.' if completed else 'Steps 1–5 completed. Step 6 stopped before any API request because OPENROUTER_API_KEY is absent or empty inside the container. Model availability, selection, and probe are pending. Do not start Step 7: this document requires Gary’s confirmation after the completed Step 6 probe.')
    if models_blocked:
        summary = 'Steps 1–5 completed. Step 6 reached OpenRouter successfully but stopped because no Mistral 7B Instruct model is listed. No generation was attempted. Stop for Gary’s review before selecting a replacement or starting Step 7.'
    lines = ['# Part 1 handover A', '',
        summary, '',
        '## Environment', '', '| Component | Version / status |', '|---|---|',
        '| Branch | experiment/2026-10-week1-d4j-replication |',
        f'| Defects4J | {env["defects4j_tag"]}, commit {env["defects4j_commit"]} |',
        '| JDK | OpenJDK 11.0.32.1, aarch64 |',
        f'| Python | {env["python"]} |',
        f'| tiktoken | {env["packages"]["tiktoken"]}, cl100k_base |',
        f'| javalang | {env["packages"]["javalang"]} |',
        '| Evaluation tools | JUnit 4.13.2; Hamcrest core 1.3; JaCoCo 0.8.8 |',
        '| Docker | Existing image reused; Compose memory limit 12 GB |',
        '| API key | ' + ('Present; confirmed inside container' if key_present else 'Absent or empty inside container') + '; value never displayed or saved |', '',
        'Existing Docker configuration passed the required Defects4J, Java and Python checks. JsonRunner compiled. JUnit reported version 4.13.2 and the JaCoCo CLI help command succeeded. Download URLs and SHA-256s are in tools/VERSIONS.md. Docker Desktop’s global memory setting was not inspected; the service limit is 12 GB.', '',
        '## Step 1: bundle', '',
        'All seven requested copied files/directories are identical to the original bundle (recursive diff). Dataset contains 477 records, including 14 Lang records and 9 distinct classes. Existing ORIGIN.md matches the required provenance. The original bundle was not modified. See results/part1-bundle-check.json.', '',
        '## Step 4: buggy Lang checkouts', '',
        '| Checkout | Class | Reused | Compile seconds | Compiled | classes.modified match | Normalized source |',
        '|---|---|---|---:|---|---|---|']
    for r in sorted(records, key=lambda x: x['bug_id']):
        source = 'equal' if r['source_comparison'] == 'equal' else f'{r["source_comparison"]} changed diff lines; comments only'
        lines.append(f'| {r["checkout"]} | {r["class"]} | {r["checkout_reused"]} | {r["compile_seconds"]} | {r["compile_ok"]} | {r["classes_match"]} | {source} |')
    lines += ['', 'All 14 compile. Lang-4b was reused after checking checkout identity and modified classes; the other 13 were checked out as buggy versions. Lang-64b differs in two Javadoc lines: “subclassd” → “subclassed” and “subclasss” → “subclasses”. This yields four removed/added lines in the unified diff. The complete sequences of Java token types and values are identical after the required normalization, confirming comment-only changes. All other normalized sources are equal. Source paths, packages, exports and timing are recorded in results/lang-records.json; raw command logs and diffs are in results/part1-d4j-logs/.', '',
        '## Step 5: prompts and tokens', '',
        'Six strings were extracted by AST from the five Mistral scripts without executing them or reading the bundle’s prompt text files. Templates were written as UTF-8 bytes without an added newline. Single-pass split-and-join substitution rendered 477 × 5 = 2,385 distinct files; inserted Java code is never processed a second time.', '',
        '| Technique | Rendered | Exact token matches | Nonexact | Difference range | Maximum tokens | Above 4,096 |',
        '|---|---:|---:|---:|---|---:|---:|']
    for tech, s in manifest['token_check'].items():
        exact = s['exact'] if s['exact'] is not None else 'N/A'
        nonexact = 477 - s['exact'] if s['exact'] is not None else 'N/A'
        diff = f'{s["min_diff"]}..{s["max_diff"]}' if s['min_diff'] is not None else 'N/A'
        lines.append(f'| {tech} | {s["rendered"]} | {exact} | {nonexact} | {diff} | {s["max_tokens"]} | {s["exceeds_4096"]} |')
    lines += ['', 'All four checked techniques match the pre-check: 428 exact and 49 positive differences of +1..+8. Every record has the same difference across ZSL/FSL/CoT/ToT. No negative difference, cross-technique discrepancy, or token-check stop condition occurred. GToT has no author token count.', '',
        'Template checks passed: ZSL contains no fewshot_example placeholder; FSL contains it exactly once and begins with the specified professional tester/examples sentence; the few-shot example starts with a newline followed by //Example Java Class:. AST extraction only accepts the three specified expressions. SHA-256s and tokenizer version are recorded in prompts/manifest.json and results/template-check.json.', '',
        '## Step 6: probe', '', probe.strip(), '',
        ('Probe artifacts are saved under ' + selection['probe_directory'] + '; see usage.json for reported cost.' if completed else 'One model-list request succeeded (HTTP 200, 466 listed models, zero matching Mistral 7B IDs). No generation request was made and no probe response exists. Generation cost is zero; completion status, finish_reason, tokens, provider, markers and first ten lines are unavailable.' if models_blocked else 'No model-list request or generation call was made, no probe response exists, and API cost incurred in this session is zero. HTTP status, finish_reason, completion tokens, provider, marker presence and first ten completion lines are unavailable until the probe succeeds.'), '',
        '## Unexpected findings and mechanical fixes', '',
        '- Expected comment-only differences were found in Lang-64b, as detailed above.',
        '- One Docker invocation from the repository root was denied access to the Docker socket by the command sandbox. It was rerun with the required escalation and succeeded; no experiment parameters changed.',
        '- A temporary-script creation tool call had a JavaScript quoting error before execution. It was corrected using apply_patch; no API call or experiment output was affected.',
        '- The existing image and author copies were reused after validation. Evaluation JARs and compiled runner bytecode remain ignored; source, version metadata, prompts, exports, and logs are committed.',
        '- User-provided configuration now supplies container credentials through docker/.env via Compose env_file. docker/.env is gitignored; its contents were not inspected or committed. The container presence check passed.', '',
        '## Resume', '',
        ('Await Gary’s confirmation before Step 7. No push was performed.' if completed else 'Await Gary’s review of unavailable Mistral 7B models. A replacement model or another provider requires an explicit experiment decision. No push was performed.' if models_blocked else 'Supply OPENROUTER_API_KEY in the terminal environment used to launch Docker Compose. Do not paste the key into a commit or handover. From docker/, run docker compose run --rm d4j bash -c \'python3 scripts/probe_model.py\'. Then regenerate this handover with scripts/part1_handover.py, commit the probe artifacts and updated handover, and stop for Gary’s review before Step 7. No push was performed.'), '']
    if check and not completed:
        lines += ['## Latest Step 6 attempt', '',
            f'Attempt recorded at {check["timestamp_utc"]}. {check["reason"]} Evidence: results/probe-preflight.json.', '',
            'The key value was never printed or saved. Step 7 remains unstarted.', '']
    if completed:
        config = json.loads(Path('results/model-config.json').read_text())
        usage = json.loads((Path(selection['probe_directory']) / 'usage.json').read_text())
        lines += ['## Authorized model deviation', '',
            config['reason'], '',
            f'Use {selection["model"]} for Step 6 and every later generation. Parameters remain temperature 0.7, max_tokens 4096, one user message and no system message. The saved model configuration is results/model-config.json. Original model-list output is preserved byte-for-byte at results/model-list.json.', '',
            'The statement that none of the paper’s four models remains callable through an API is the user’s availability finding as of 2026-10-03. The saved OpenRouter list directly supports the absence of Mistral 7B. This run uses a replacement model and does not numerically replicate the paper.', '',
            f'Probe usage: {usage["prompt_tokens"]} prompt tokens, {usage["completion_tokens"]} completion tokens (including {usage.get("completion_tokens_details", {}).get("reasoning_tokens", 0)} reasoning tokens). Reported cost: ${usage.get("cost")}. Request parameters were verified against the fixed protocol, assistant content was verified byte-for-byte against the raw response, and the original saved model list was verified identical to results/model-list.json.', '',
            'Step 7 has not started. Stop for Gary’s review; no push was performed.', '']
    Path('docs/handover-part1-a.md').write_text('\n'.join(lines))
    print('Wrote docs/handover-part1-a.md')

if __name__ == '__main__':
    main()
