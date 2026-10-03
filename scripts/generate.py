"""Run the 70 fixed-protocol Lang generations; retain every HTTP attempt."""
import concurrent.futures
import json
import os
import threading
import time
from datetime import datetime, timezone
from pathlib import Path
import requests

TECHS=['ZSL','FSL','CoT','ToT','GToT']
LOCK=threading.Lock()

def save(path,value):
    path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n')

def generate(record,tech,config,key):
    root=Path('runs/lang')/str(record['bug-id'])/record['class']/tech
    prompt=Path('prompts/rendered/Lang')/str(record['bug-id'])/record['class']/(tech+'.txt')
    body={'model':config['model'],'temperature':0.7,'max_tokens':4096,'messages':[{'role':'user','content':prompt.read_text()}]}
    root.mkdir(parents=True,exist_ok=True)
    if (root/'run.json').exists():
        raise RuntimeError(f'Existing run at {root}; refuse duplicate generation')
    for attempt in range(1,5):
        dest=root if attempt==1 else root/f'attempt-{attempt}'
        dest.mkdir(exist_ok=True); save(dest/'request.json',body)
        stamp=datetime.now(timezone.utc).isoformat(); start=time.monotonic()
        try:
            response=requests.post('https://openrouter.ai/api/v1/chat/completions',headers={'Authorization':'Bearer '+key},json=body,timeout=360)
        except requests.RequestException as exc:
            save(dest/'run.json',{'timestamp':stamp,'latency_seconds':round(time.monotonic()-start,3),'status':'transport_error','exception_type':type(exc).__name__,'attempt':attempt})
            raise RuntimeError(f'Transport error at {dest}; retained evidence, no blind retry') from None
        (dest/'raw-response.json').write_bytes(response.content)
        meta={'timestamp':stamp,'latency_seconds':round(time.monotonic()-start,3),'status':response.status_code,'attempt':attempt,'model':config['model'],'bug_id':record['bug-id'],'class':record['class'],'technique':tech}
        save(dest/'run.json',meta)
        if not response.ok:
            with LOCK: print(f'HTTP {response.status_code}: {root} attempt {attempt}',flush=True)
            if attempt<4: time.sleep(10); continue
            raise RuntimeError(f'HTTP retry limit reached at {root}')
        result=response.json()
        if 'error' in result or not result.get('choices'):
            raise RuntimeError(f'Well-formed response without completion at {dest}; no retry')
        choice=result['choices'][0]; content=choice['message'].get('content') or ''
        (dest/'response.md').write_bytes(content.encode('utf-8')); save(dest/'usage.json',result.get('usage',{}))
        meta.update({'finish_reason':choice.get('finish_reason'),'provider':result.get('provider'),'response_id':result.get('id'),'content_empty':not bool(content),'start_marker':'###Test START##' in content,'end_marker':'###Test END##' in content})
        save(dest/'run.json',meta)
        usage=result.get('usage',{})
        with LOCK: print(f'Lang-{record["bug-id"]} {record["class"]} {tech}: {meta["finish_reason"]}, {usage.get("completion_tokens")} tokens, empty={not bool(content)}',flush=True)
        return {'bug_id':record['bug-id'],'class':record['class'],'technique':tech,'directory':str(dest),'finish_reason':meta['finish_reason'],'completion_tokens':usage.get('completion_tokens'),'cost':usage.get('cost'),'content_empty':not bool(content),'attempt':attempt,'provider':meta['provider']}

def main():
    key=os.environ.get('OPENROUTER_API_KEY')
    if not key: raise RuntimeError('OPENROUTER_API_KEY missing')
    config=json.loads(Path('results/model-config.json').read_text())
    selection=json.loads(Path('results/model-selection.json').read_text())
    assert config['model']==selection['model']=='openai/gpt-oss-120b'
    assert config['temperature']==0.7 and config['max_tokens']==4096 and config['message_roles']==['user']
    records=[r for r in json.loads(Path('bundle/Defects4J-dataset.json').read_text(encoding='utf-8',errors='ignore')) if r['project_name']=='Lang']
    start=time.monotonic(); rows=[]; errors=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        tasks={pool.submit(generate,r,t,config,key):(r,t) for r in records for t in TECHS}
        for future in concurrent.futures.as_completed(tasks):
            try: rows.append(future.result())
            except Exception as exc: errors.append(str(exc)); print(str(exc),flush=True)
            save(Path('results/generation-progress.json'),{'completed':len(rows),'expected':70,'errors':errors,'runs':sorted(rows,key=lambda r:(int(r['bug_id']),TECHS.index(r['technique'])))})
    rows.sort(key=lambda r:(int(r['bug_id']),TECHS.index(r['technique'])))
    summary={'model':config['model'],'generated':len(rows),'expected':70,'concurrency':2,'wall_seconds':round(time.monotonic()-start,3),'cost_reported':sum(r['cost'] or 0 for r in rows),'cost_missing_count':sum(r['cost'] is None for r in rows),'length_finishes':sum(r['finish_reason']=='length' for r in rows),'empty_content':sum(r['content_empty'] for r in rows),'errors':errors,'runs':rows}
    save(Path('results/generation-summary.json'),summary)
    lines=['# Lang generation results','','Cells: finish_reason / completion tokens; EMPTY flags empty assistant content.','','| Bug / class | '+' | '.join(TECHS)+' |','|---|'+'---|'*5]
    by={(str(r['bug_id']),r['technique']):r for r in rows}
    for record in sorted(records,key=lambda r:int(r['bug-id'])):
        cells=[]
        for tech in TECHS:
            r=by.get((str(record['bug-id']),tech))
            cells.append(f'{r["finish_reason"]} / {r["completion_tokens"]}'+(' EMPTY' if r['content_empty'] else '') if r else 'ERROR')
        lines.append(f'| {record["bug-id"]} / {record["class"]} | '+' | '.join(cells)+' |')
    lines+=['',f'Generated: {len(rows)}/70. Length finishes: {summary["length_finishes"]}. Empty content: {summary["empty_content"]}. Reported cost: ${summary["cost_reported"]:.8f}. Wall time: {summary["wall_seconds"]} seconds.','']
    Path('results/generation.md').write_text('\n'.join(lines))
    if errors or len(rows)!=70: raise RuntimeError('Generation incomplete; see saved summary')

if __name__=='__main__': main()
