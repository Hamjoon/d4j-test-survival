"""Evaluate immutable Lang generations and write the Part 1 result tables."""
import argparse
from collections import Counter
import csv
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import javalang

TECHS=['ZSL','FSL','CoT','ToT','GToT']
RESULTS=Path('results')


def save(path,value):
    path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n')


def read_csv(path):
    with open(path,encoding='utf-8',newline='') as f: return list(csv.DictReader(f))


def write_csv(path,fields,rows):
    with open(path,'w',encoding='utf-8',newline='') as f:
        w=csv.DictWriter(f,fieldnames=fields); w.writeheader(); w.writerows(rows)


def manifest(): return json.loads((RESULTS/'evaluation-manifest.json').read_text())


def record_map():
    return {str(r['bug_id']):r for r in json.loads((RESULTS/'lang-records.json').read_text())}


def extract():
    generation=json.loads((RESULTS/'generation-summary.json').read_text())
    assert generation['generated']==70 and not generation['errors']
    for r in generation['runs']:
        folder=Path(r['directory']); (folder/'raw.java').write_bytes((folder/'response.md').read_bytes())
    script=Path('bundle/authors-extraction/extract_tests.py')
    digest=hashlib.sha256(script.read_bytes()).hexdigest()
    subprocess.run(['python3',str(script),'runs/lang','--csv','results/extraction.csv'],check=True)
    extracted=read_csv(RESULTS/'extraction.csv')
    assert len(extracted)==70
    by={str(Path(r['directory'])/'raw.java'):r for r in generation['runs']}
    rows=[]
    for ex in extracted:
        gen=by[ex['file_path']]; text=ex['combine']
        row={**gen,'msr':ex['msr_detected']=='True','csr':ex['csr_success']=='True','materialized':False,'file':None,'class_name':None,'status':'no-code'}
        if row['csr']:
            name=re.search(r'public\s+class\s+(\w+)',text) or re.search(r'class\s+(\w+)',text)
            if name:
                row['class_name']=name.group(1)
                dest=Path('generated')/f'Lang-{gen["bug_id"]}'/gen['technique']/(row['class_name']+'.java')
                dest.parent.mkdir(parents=True,exist_ok=True)
                assert not dest.exists(), f'Refuse overwriting generated test {dest}'
                dest.write_bytes(text.encode('utf-8'))
                row.update({'materialized':True,'file':str(dest),'extracted_sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'status':'syntax'})
            else: row['status']='no-class'
        rows.append(row)
    rows.sort(key=lambda r:(int(r['bug_id']),TECHS.index(r['technique'])))
    save(RESULTS/'evaluation-manifest.json',rows)
    save(RESULTS/'extraction-provenance.json',{'script':str(script),'sha256':digest,'responses':70,'normalization':'none; materialized bytes equal unchanged combine text'})
    lines=['# Extraction summary','','Unmodified author reimplementation SHA-256: '+digest+'.','','| Technique | MSR / 14 | CSR / 14 | Materialized | No class name |','|---|---:|---:|---:|---:|']
    for tech in TECHS:
        group=[r for r in rows if r['technique']==tech]
        lines.append(f'| {tech} | {sum(r["msr"] for r in group)} | {sum(r["csr"] for r in group)} | {sum(r["materialized"] for r in group)} | {sum(r["status"]=="no-class" for r in group)} |')
    lines+=['','Materialized files:','']+[f'- {r["file"]}' for r in rows if r['materialized']]
    Path('results/extraction-summary.md').write_text('\n'.join(lines)+'\n')
    print('\n'.join(lines[:10]),flush=True)


def syntax():
    rows=manifest(); out=[]
    for row in rows:
        if not row['materialized']: continue
        text=Path(row['file']).read_text()
        try:
            javalang.parse.parse(text); ok=True; error=''
        except (javalang.parser.JavaSyntaxError,javalang.tokenizer.LexerError) as exc:
            ok=False; error=str(exc) or f'{type(exc).__name__}: {getattr(exc,"description","")} at {getattr(exc,"at","")}'
        row.update({'syntax_ok':ok,'syntax_error':error,'status':'compile' if ok else 'syntax'})
        out.append({'bug_id':row['bug_id'],'class':row['class'],'technique':row['technique'],'file':row['file'],'syntax_ok':ok,'error':error})
    write_csv(RESULTS/'syntax.csv',['bug_id','class','technique','file','syntax_ok','error'],out)
    save(RESULTS/'evaluation-manifest.json',rows)
    print('Syntax OK by technique:',{t:sum(r.get('syntax_ok',False) for r in rows if r['technique']==t) for t in TECHS},flush=True)


CATEGORIES=[('CFS',r'cannot find symbol'),('PDNE',r'package .* does not exist'),('PAI',r'has private access'),('IT',r'incompatible types'),('CAM',r'constructor .* cannot be applied'),('MAM',r'method .* cannot be applied'),('DCD',r'duplicate class'),('PCR',r'class .* is public, should be declared in a file named'),('SCI',r'non-static .* cannot be referenced from a static context'),('UE',r'unreported exception'),('VNI',r'might not have been initialized'),('WAP',r'attempting to assign weaker access privileges'),('AR',r'reference to .* is ambiguous'),('USL',r'unclosed string literal'),('UCL',r'unclosed character literal')]


def compile_tests():
    rows=manifest(); out=[]; errors=[]
    for row in rows:
        if not row.get('syntax_ok'): continue
        folder=Path(row['file']).parent; classes=folder/'classes'; classes.mkdir(exist_ok=True)
        cp=(RESULTS/'d4j-export'/f'Lang-{row["bug_id"]}b.cp.test').read_text().strip()+':tools/junit-4.13.2.jar:tools/hamcrest-core-1.3.jar'
        start=time.monotonic()
        result=subprocess.run(['javac','-d',str(classes),'-cp',cp,row['file']],capture_output=True,text=True)
        elapsed=round(time.monotonic()-start,3)
        (folder/'javac.err').write_text(result.stderr); (folder/'javac.out').write_text(result.stdout)
        lines=[line for line in result.stderr.splitlines() if re.search(r'\berror:',line)]
        counts=Counter()
        for line in lines:
            cat=next((name for name,pattern in CATEGORIES if re.search(pattern,line)), 'other')
            counts[cat]+=1; errors.append({'bug_id':row['bug_id'],'class':row['class'],'technique':row['technique'],'file':row['file'],'category':cat,'error_line':line})
        row.update({'compile_ok':result.returncode==0,'compile_exit_status':result.returncode,'compile_error_count':len(lines),'compile_categories':dict(counts),'compile_seconds':elapsed,'status':'run-error' if result.returncode==0 else 'compile'})
        out.append({'bug_id':row['bug_id'],'class':row['class'],'technique':row['technique'],'file':row['file'],'exit_status':result.returncode,'compiles':result.returncode==0,'error_count':len(lines),'category_counts':json.dumps(dict(counts),sort_keys=True),'stderr_file':str(folder/'javac.err'),'stderr':result.stderr,'wall_seconds':elapsed})
        print(f'Compile Lang-{row["bug_id"]} {row["technique"]}: {result.returncode}, {len(lines)} errors',flush=True)
    write_csv(RESULTS/'compile.csv',['bug_id','class','technique','file','exit_status','compiles','error_count','category_counts','stderr_file','stderr','wall_seconds'],out)
    write_csv(RESULTS/'compile-errors.csv',['bug_id','class','technique','file','category','error_line'],errors)
    save(RESULTS/'evaluation-manifest.json',rows)


def run_tests():
    rows=manifest(); records=record_map(); runs=[]; coverage=[]
    for row in rows:
        if not row.get('compile_ok'): continue
        record=records[str(row['bug_id'])]; folder=Path(row['file']).parent
        text=Path(row['file']).read_text(); match=re.search(r'^\s*package\s+([\w.]+)\s*;',text,re.M)
        fqcn=(match.group(1)+'.' if match else '')+row['class_name']
        cp=(RESULTS/'d4j-export'/f'Lang-{row["bug_id"]}b.cp.test').read_text().strip()
        classpath=f'{folder}/classes:tools/runner:{cp}:tools/junit-4.13.2.jar:tools/hamcrest-core-1.3.jar'
        cmd=['timeout','120','java',f'-javaagent:tools/jacocoagent.jar=destfile={folder}/jacoco.exec,includes={record["package"]}.*','-cp',classpath,'JsonRunner',fqcn]
        start=time.monotonic(); result=subprocess.run(cmd,capture_output=True,text=True)
        elapsed=round(time.monotonic()-start,3)
        (folder/'run.jsonl').write_text(result.stdout); (folder/'run.err').write_text(result.stderr)
        events=[]; noise=[]
        for line in result.stdout.splitlines():
            try:
                event=json.loads(line)
                if isinstance(event,dict) and ('summary' in event or 'method' in event): events.append(event)
                else: noise.append(line)
            except json.JSONDecodeError: noise.append(line)
        summaries=[e['summary'] for e in events if 'summary' in e]
        summary=summaries[-1] if summaries else {}
        methods=[e for e in events if 'method' in e]
        run_error='' if result.returncode==0 and summaries else ('timeout' if result.returncode==124 else f'exit={result.returncode}; summary_present={bool(summaries)}')
        if run_error: run_error+='; '+ '\n'.join(result.stderr.splitlines()[-20:])
        row.update({'fqcn':fqcn,'run_exit_status':result.returncode,'run_error':run_error,'tests_run':summary.get('run',0),'passed':sum(e.get('status')=='pass' for e in methods),'failed':summary.get('failures',0),'ignored':summary.get('ignored',0),'run_seconds':elapsed,'status':'run-error' if run_error else 'ran'})
        save(folder/'run-summary.json',{'command':cmd,'exit_status':result.returncode,'summary':summary,'methods':methods,'stdout_non_json':noise,'run_error':run_error,'wall_seconds':elapsed})
        runs.append({k:row[k] for k in ['bug_id','class','technique','file','fqcn','tests_run','passed','failed','ignored','run_error','run_exit_status','run_seconds']})
        cov={'bug_id':row['bug_id'],'class':row['class'],'technique':row['technique'],'file':row['file'],'line_coverage':None,'instruction_coverage':None,'method_coverage':None,'coverage_error':''}
        if (folder/'jacoco.exec').exists():
            checkout=Path('/work/d4j')/f'Lang-{row["bug_id"]}b'
            report=subprocess.run(['java','-jar','tools/jacococli.jar','report',str(folder/'jacoco.exec'),'--classfiles',str(checkout/record['dir.bin.classes']),'--sourcefiles',str(checkout/record['dir.src.classes']),'--csv',str(folder/'jacoco.csv')],capture_output=True,text=True)
            (folder/'jacoco-report.out').write_text(report.stdout); (folder/'jacoco-report.err').write_text(report.stderr)
            if report.returncode: cov['coverage_error']=f'JaCoCo report exit {report.returncode}'
            else:
                candidates=[r for r in read_csv(folder/'jacoco.csv') if r['CLASS']==row['class'] and r['PACKAGE'].replace('/','.')==record['package']]
                if len(candidates)!=1: cov['coverage_error']=f'Expected one CUT row, found {len(candidates)}'
                else:
                    target=candidates[0]
                    for kind,label in [('LINE','line'),('INSTRUCTION','instruction'),('METHOD','method')]:
                        covered=int(target[kind+'_COVERED']); missed=int(target[kind+'_MISSED']); total=covered+missed
                        cov[label+'_coverage']=covered/total if total else None
        else: cov['coverage_error']='No jacoco.exec produced'
        row['coverage']=cov; coverage.append(cov)
        print(f'Run Lang-{row["bug_id"]} {row["technique"]}: {row["passed"]}/{row["tests_run"]} pass, CUT line coverage {cov["line_coverage"]}, error={run_error or cov["coverage_error"] or "none"}',flush=True)
    write_csv(RESULTS/'run.csv',['bug_id','class','technique','file','fqcn','tests_run','passed','failed','ignored','run_error','run_exit_status','run_seconds'],runs)
    write_csv(RESULTS/'coverage.csv',['bug_id','class','technique','file','line_coverage','instruction_coverage','method_coverage','coverage_error'],coverage)
    save(RESULTS/'evaluation-manifest.json',rows)


def reference():
    path=Path('bundle/extraction_outputs/Defects4J_filtered_valid_outputs.csv')
    rows=read_csv(path); lang=[r for r in rows if r['project_name']=='Lang']
    regex=re.compile(r'^(ZEROSHOT|FEWSHOT|COT|TOT)-'); mapping={'ZEROSHOT':'ZSL','FEWSHOT':'FSL','COT':'CoT','TOT':'ToT'}
    counts=Counter(); totals=Counter(); unmatched=[]
    for row in lang:
        match=regex.match(row['prompt_engineering-iter_number'])
        if not match: unmatched.append(row); continue
        tech=mapping[match.group(1)]; totals[tech]+=1
        if row['Syntax_and_import_OK']=='True': counts[tech]+=1
    result={'source':str(path),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'lang_rows':len(lang),'regex':regex.pattern,'regex_covers_every_lang_row':not unmatched,'unmatched_count':len(unmatched),'unmatched_identifiers':sorted({r['prompt_engineering-iter_number'] for r in unmatched}),'valid_counts':dict(counts),'total_counts':dict(totals),'expected_valid_counts':{'ZSL':369,'FSL':306,'CoT':303,'ToT':353},'interpretation':'Historical GPT-3.5-turbo reference rate only; not a comparison target.'}
    result['matches_expected']=result['valid_counts']==result['expected_valid_counts']
    result['all_lang_rows_have_true_flag']=all(r['Syntax_and_import_OK']=='True' for r in lang)
    result['exact_unique_lang_rows']=len({tuple(sorted(r.items())) for r in lang})
    save(RESULTS/'authors-lang-reference.json',result)
    return result


def reconcile_runtime_counts():
    """Retain observed method counts when a timeout prevents the JUnit summary."""
    rows=manifest(); out=[]
    for row in rows:
        if not row.get('compile_ok'): continue
        path=Path(row['file']).parent/'run-summary.json'
        observed=json.loads(path.read_text())
        methods=observed['methods']; summary=observed['summary']
        row['counts_partial']=not bool(summary)
        if not summary:
            completed={e['method'] for e in methods if e.get('status') in ['pass','fail']}
            failed={e['method'] for e in methods if e.get('status')=='fail'}
            ignored={e['method'] for e in methods if e.get('status')=='ignored'}
            row['tests_run']=len(completed); row['failed']=len(failed); row['ignored']=len(ignored)
            observed['observed_completed_methods']=len(completed)
            observed['counts_partial']=True
            save(path,observed)
        out.append({k:row[k] for k in ['bug_id','class','technique','file','fqcn','tests_run','passed','failed','ignored','run_error','run_exit_status','run_seconds','counts_partial']})
    write_csv(RESULTS/'run.csv',['bug_id','class','technique','file','fqcn','tests_run','passed','failed','ignored','run_error','run_exit_status','run_seconds','counts_partial'],out)
    save(RESULTS/'evaluation-manifest.json',rows)
    print('Runtime counts reconciled; partial-summary files:',sum(r['counts_partial'] for r in out),flush=True)


def report():
    rows=manifest(); ref=reference()
    lines=['# Lang Part 1 matrix','','Model: openai/gpt-oss-120b. One generation per record and technique; no generated-test edits or normalization.','','| technique | generated | MSR | CSR | syntax ok | compiles | files that run | test methods run / passed | CUT line cov (mean over compiled files) |','|---|---:|---:|---:|---:|---:|---:|---|---:|']
    matrix=[]
    for tech in TECHS+['pooled']:
        group=rows if tech=='pooled' else [r for r in rows if r['technique']==tech]
        compiled=[r for r in group if r.get('compile_ok')]
        values=[r.get('coverage',{}).get('line_coverage') for r in compiled]
        mean=sum(values)/len(values) if values and all(v is not None for v in values) else None
        entry={'technique':tech,'generated':len(group),'MSR':sum(r['msr'] for r in group),'CSR':sum(r['csr'] for r in group),'syntax_ok':sum(r.get('syntax_ok',False) for r in group),'compiles':len(compiled),'files_that_run':sum(r['status']=='ran' for r in group),'tests_run':sum(r.get('tests_run',0) for r in group),'passed':sum(r.get('passed',0) for r in group),'mean_cut_line_coverage':mean}
        matrix.append(entry)
        lines.append(f'| {tech} | {entry["generated"]} | {entry["MSR"]} | {entry["CSR"]} | {entry["syntax_ok"]} | {entry["compiles"]} | {entry["files_that_run"]} | {entry["tests_run"]} / {entry["passed"]} | '+(f'{mean:.2%}' if mean is not None else 'N/A')+' |')
    lines+=['','Coverage is the arithmetic mean of per-file CUT line ratios over compiled files, including files whose tests fail. A missing coverage measurement yields N/A rather than dropping that file. JUnit failures are retained as results; they do not make a file a run-error when the runner produces its summary.','']
    normal=[r for r in rows if r['status']=='ran']
    partial=[r for r in rows if r.get('counts_partial')]
    if partial:
        lines += [f'Method totals are observed completed results and include partial timeout runs: the 17 completed files account for {sum(r["tests_run"] for r in normal)} methods / {sum(r["passed"] for r in normal)} passes; the two timeout files add {sum(r["tests_run"] for r in partial)} completed methods / {sum(r["passed"] for r in partial)} passes. The unfinished methods are uncounted, so pooled run counts are lower bounds.', '']
    (RESULTS/'lang-part1-matrix.md').write_text('\n'.join(lines)); save(RESULTS/'lang-part1-matrix.json',matrix)
    cells=['# Lang Part 1 per-record results','','Codes: no-code = CSR unsuccessful; no-class = CSR successful without class name; syntax = parse failure; compile = javac failure; run-error = timeout/JVM/runner error; ran = runner completed (passed/total).','','| Bug / class | '+' | '.join(TECHS)+' |','|---|'+'---|'*5]
    by={(str(r['bug_id']),r['technique']):r for r in rows}
    for bug,record in sorted(record_map().items(),key=lambda kv:int(kv[0])):
        values=[]
        for tech in TECHS:
            row=by[(bug,tech)]; values.append(row['status']+(f' {row["passed"]}/{row["tests_run"]}' if row['status']=='ran' else ''))
        cells.append(f'| {bug} / {record["class"]} | '+' | '.join(values)+' |')
    (RESULTS/'lang-part1-per-record.md').write_text('\n'.join(cells)+'\n')
    print('\n'.join(lines),flush=True); print('Historical reference:',ref['valid_counts'],'regex covers all:',ref['regex_covers_every_lang_row'],flush=True)


def main():
    p=argparse.ArgumentParser(); p.add_argument('step',choices=['8','9','10','11','12']); args=p.parse_args()
    start=time.monotonic(); stamp=datetime.now(timezone.utc).isoformat()
    {'8':extract,'9':syntax,'10':compile_tests,'11':run_tests,'12':report}[args.step]()
    if args.step=='11': reconcile_runtime_counts()
    path=RESULTS/'stage-times.json'; times=json.loads(path.read_text()) if path.exists() else {}
    times[args.step]={'started_utc':stamp,'wall_seconds':round(time.monotonic()-start,3)}; save(path,times)

if __name__=='__main__': main()
