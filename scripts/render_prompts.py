"""Render prompts with single-pass substitution and verify author token counts."""
import csv
import hashlib
import importlib.metadata
import json
import re
from pathlib import Path
import tiktoken

TECHS=['ZSL','FSL','CoT','ToT','GToT']
FIELDS={'ZSL':'token_number_zeroshot','FSL':'token_number_fewshot','CoT':'token_number_cot','ToT':'token_number_tot'}
SPLIT=re.compile(r'(\{class_name\}|\{source_code\}|\{fewshot_example\})')

def main():
    data=json.loads(Path('bundle/Defects4J-dataset.json').read_text(encoding='utf-8',errors='ignore'))
    templates={tech:Path(f'prompts/templates/{tech}.txt').read_text() for tech in TECHS}
    example=Path('prompts/templates/fewshot_example.txt').read_text()
    encoder=tiktoken.get_encoding('cl100k_base')
    rows=[]; summaries={}; counts={tech:[] for tech in TECHS}; per_record=[]; destinations=set()
    for index,record in enumerate(data):
        values={'{class_name}':record['class'],'{source_code}':record['source_code'],'{fewshot_example}':example}
        diffs={}
        for tech in TECHS:
            prompt=''.join(values.get(piece,piece) for piece in SPLIT.split(templates[tech]))
            path=Path('prompts/rendered')/record['project_name']/str(record['bug-id'])/record['class']/(tech+'.txt')
            assert path not in destinations, f'Duplicate destination {path}'
            destinations.add(path); path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(prompt.encode('utf-8'))
            n=len(encoder.encode(prompt,disallowed_special=())); counts[tech].append(n)
            if tech in FIELDS:
                authors=int(record[FIELDS[tech]]); diff=n-authors; diffs[tech]=diff
                rows.append([record['project_name'],record['bug-id'],record['class'],tech,n,authors,diff])
        per_record.append(diffs)
    with open('results/token-check.csv','w',newline='') as f:
        writer=csv.writer(f); writer.writerow(['project','bug_id','class','tech','ours','authors','diff']); writer.writerows(rows)
    errors=[]
    for tech in TECHS:
        diffs=[x[tech] for x in per_record] if tech in FIELDS else []
        summaries[tech]={'rendered':len(counts[tech]),'exact':diffs.count(0) if diffs else None,'min_diff':min(diffs) if diffs else None,'max_diff':max(diffs) if diffs else None,'max_tokens':max(counts[tech]),'exceeds_4096':sum(n>4096 for n in counts[tech])}
        if diffs and (diffs.count(0)<420 or min(diffs)<0): errors.append(f'{tech}: exact-count/negative-diff stop condition')
    if any(len(set(x.values()))!=1 for x in per_record): errors.append('Diff varies across techniques for a record')
    expected=all(summaries[t]['exact']==428 and summaries[t]['max_diff']<=8 for t in FIELDS)
    if not expected: errors.append('Result outside stated pre-check range (428 exact; positive differences 1..8)')
    lines=['# Prompt token check','','Tokenizer: cl100k_base (tiktoken '+importlib.metadata.version('tiktoken')+').','','| Technique | Rendered | Exact | Diff min/max | Max tokens | Above 4,096 |','|---|---:|---:|---|---:|---:|']
    for tech,s in summaries.items(): lines.append(f'| {tech} | {s["rendered"]} | {s["exact"]} | {s["min_diff"]}/{s["max_diff"]} | {s["max_tokens"]} | {s["exceeds_4096"]} |')
    lines+=['','Every record has the same token difference across ZSL, FSL, CoT and ToT.' if not any(len(set(x.values()))!=1 for x in per_record) else 'Cross-technique differences found.','',f'Stop conditions: {errors or "none"}.']
    Path('results/token-check.md').write_text('\n'.join(lines)+'\n')
    manifest={'template_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(Path('prompts/templates').glob('*.txt'))},'tokenizer':'cl100k_base','tokenizer_version':importlib.metadata.version('tiktoken'),'dataset_records':len(data),'rendered_files':len(destinations),'token_check':summaries,'stop_conditions':errors}
    Path('prompts/manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('\n'.join(lines))
    if errors: raise RuntimeError('; '.join(errors))

if __name__=='__main__': main()
