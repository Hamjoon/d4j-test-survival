"""Verify, compile and export the fourteen buggy Lang records."""
import difflib
import json
import re
import subprocess
import time
from pathlib import Path
import javalang

ROOT = Path('/work')
OUT = ROOT / 'results'
LOG = OUT / 'part1-d4j-logs'
EXP = OUT / 'd4j-export'
PROPS = ['classes.modified', 'dir.src.classes', 'dir.src.tests', 'dir.bin.classes', 'dir.bin.tests', 'cp.compile', 'cp.test', 'tests.relevant', 'tests.trigger']

def run(args, name):
    start = time.monotonic()
    p = subprocess.run(args, capture_output=True, text=True)
    elapsed = round(time.monotonic()-start, 3)
    (LOG/name).write_text(p.stdout+'\n[stderr]\n'+p.stderr+f'\n[exit] {p.returncode}\n[seconds] {elapsed}\n')
    if p.returncode: raise RuntimeError(f'Command failed: {name}')
    return p.stdout.strip(), elapsed

def normalize(text):
    text = text.replace('\r\n', '\n')
    text = ''.join(c for c in text if ord(c)<128)
    return '\n'.join(line.rstrip() for line in text.splitlines()).rstrip()

def tokens(text):
    return [(type(t).__name__,t.value) for t in javalang.tokenizer.tokenize(text)]

def main():
    LOG.mkdir(exist_ok=True); EXP.mkdir(exist_ok=True)
    data=json.loads((ROOT/'bundle/Defects4J-dataset.json').read_text(encoding='utf-8',errors='ignore'))
    rows=[]
    try:
        for index,record in enumerate(data):
            if record['project_name']!='Lang': continue
            bug=str(record['bug-id']); version=f'Lang-{bug}b'; checkout=ROOT/'d4j'/version
            print(f'Check {version} {record["class"]}',flush=True)
            row={'index':index,'bug_id':int(bug),'class':record['class'],'checkout':version}
            rows.append(row)
            config=checkout/'.defects4j.config'
            if config.exists():
                values=dict(line.split('=',1) for line in config.read_text().splitlines() if '=' in line and not line.startswith('#'))
                if values.get('pid')!='Lang' or values.get('vid')!=bug+'b': raise RuntimeError(f'Checkout identity mismatch: {version}')
                row['checkout_reused']=True
            else:
                _,row['checkout_seconds']=run(['defects4j','checkout','-p','Lang','-v',bug+'b','-w',str(checkout)],version+'-checkout.txt')
                row['checkout_reused']=False
            modified,_=run(['defects4j','export','-p','classes.modified','-w',str(checkout)],version+'-classes.modified.txt')
            expected=sorted(r['class'] for r in data if r['project_name']=='Lang' and str(r['bug-id'])==bug)
            row['classes_modified']=modified.split(); row['classes_match']=sorted(x.rsplit('.',1)[-1] for x in modified.split())==expected
            if not row['classes_match']: raise RuntimeError(f'Modified classes mismatch: {version}')
            _,row['compile_seconds']=run(['defects4j','compile','-w',str(checkout)],version+'-compile.txt')
            row['compile_ok']=True
            exports={}
            for prop in PROPS:
                value,_=run(['defects4j','export','-p',prop,'-w',str(checkout)],version+'-'+prop+'.txt')
                exports[prop]=value; (EXP/(version+'.'+prop)).write_text(value+'\n')
            (EXP/(version+'.txt')).write_text('\n\n'.join(p+'\n'+exports[p] for p in PROPS)+'\n')
            row['exports']=exports
            source=record['source_code']
            package=re.search(r'^\s*package\s+([\w.]+)\s*;',source,re.M)
            row['package']=package.group(1) if package else ''
            relative=Path(exports['dir.src.classes'])/row['package'].replace('.','/')/(record['class']+'.java')
            row['source_path']=str(relative); row['dir.src.classes']=exports['dir.src.classes']; row['dir.bin.classes']=exports['dir.bin.classes']; row['cp.test']=exports['cp.test']
            original=normalize(source); actual=normalize((checkout/relative).read_text(encoding='utf-8',errors='ignore'))
            diff=list(difflib.unified_diff(original.splitlines(),actual.splitlines(),fromfile='dataset',tofile=version,lineterm=''))
            (LOG/(version+'-source.diff')).write_text('\n'.join(diff)+'\n' if diff else '')
            row['source_comparison']='equal' if original==actual else sum(1 for x in diff if x.startswith(('+','-')) and not x.startswith(('+++','---')))
            row['comment_only']=tokens(original)==tokens(actual)
            if not row['comment_only']: raise RuntimeError(f'Code difference: {version}; see source diff')
            print(f'{version}: compiled in {row["compile_seconds"]}s; source {row["source_comparison"]}',flush=True)
    finally:
        (OUT/'lang-records.json').write_text(json.dumps(rows,indent=2)+'\n')

if __name__=='__main__': main()
