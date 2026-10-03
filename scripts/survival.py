"""Step 6: execute immutable, selected populations against each target checkout."""
from collections import Counter, defaultdict
import csv
from datetime import datetime, timezone
import fcntl
import hashlib
import json
from pathlib import Path
import time
from generate_rounds import load, save, sha
from p2_baseline import compile_file, JARS
from run_class import run_class

STATUSES = ['pass','fail','error','timeout','not-run','compile-fail','absent']


def file_result(group, point):
    first = group[0]
    source = Path(first['file'])
    assert sha(source) == first['source_sha256']
    base = Path('survival') / f'Lang-{first["bug_id"]}' / point['id']
    if first['population'] == 'dev':
        classes = base / 'dev/classes' / first['test_class']
        folder = base / 'dev' / first['test_class']
    else:
        folder = base / 'llm' / f'r{first["round"]}' / first['technique']
        classes = folder / 'classes'
    names = sorted(m['method'] for m in group)
    assert len(names) == len(set(names))
    evidence = dict(file=str(source), source_sha256=first['source_sha256'], selected_methods=names,
        target=point['id'], fqcn=first['test_class'], population=first['population'])
    path = folder / 'evaluation.json'
    if path.exists():
        result = load(path)
        assert result['input'] == evidence
        return result
    cp = (Path('results/d4j-export') / (point['id']+'.cp.test')).read_text().strip()
    compile_path = folder / 'compile.json'
    compiled = load(compile_path) if compile_path.exists() else compile_file(source, classes, cp, folder)
    result = dict(input=evidence,compile=compiled,folder=str(folder),classes=str(classes))
    if compiled['compile_ok']:
        output = folder / 'run.jsonl'
        summary_path = output.with_suffix('.summary.json')
        summary = load(summary_path) if summary_path.exists() else run_class(first['test_class'],
            f'{classes}:tools/runner:{cp}:{JARS}',output,working_directory=Path('d4j')/point['id'],include_methods=names)
        assert summary['selected_methods']==names
        assert set(m['method'] for m in summary['methods'])==set(names)
        result.update(run_file=str(output),methods=summary['methods'],counts=summary['counts'],
            run_seconds=summary['wall_seconds'],list_error=summary['list_error'])
    assert sha(source) == first['source_sha256']
    save(path,result)
    return result


def main():
    started=time.monotonic()
    population=load('results/p2-population.json')
    timeline=load('results/p2-timeline.json')
    input_hash=sha('results/p2-population.json')
    assert load('results/p2-generation-rounds.json')['dedup_complete']
    state_path=Path('results/p2-survival-progress.json')
    state=load(state_path) if state_path.exists() else dict(complete=False,population_sha256=input_hash,
        started_at=datetime.now(timezone.utc).isoformat(),sessions=[],completed_points=[])
    assert state['population_sha256']==input_hash
    session=dict(started_at=datetime.now(timezone.utc).isoformat())
    state['sessions'].append(session)
    rows, files = [], []
    try:
        for record in timeline['records']:
            bug=record['bug_id']
            methods=[m for m in population['methods'] if m['bug_id']==bug]
            groups=defaultdict(list)
            for m in methods:
                assert m['baseline_status']=='pass' and not m.get('is_duplicate',False)
                groups[m['file']].append(m)
            for point in record['timepoints']:
                measured={}
                if point['status']=='present':
                    for source, group in groups.items():
                        result=file_result(group,point)
                        measured[source]=result
                        files.append(dict(bug_id=bug,timepoint=point['id'],**result))
                point_rows=[]
                for m in methods:
                    status,exception,message,milliseconds,launch,categories,log='absent',None,'CUT absent at its original package path',None,None,'',''
                    if point['status']=='present':
                        result=measured[m['file']]
                        categories='|'.join(sorted(result['compile']['categories']))
                        log=result['folder']
                        if not result['compile']['compile_ok']:
                            status='compile-fail'
                            message=Path(result['compile']['stderr_file']).read_text()[:500]
                        else:
                            event=next(e for e in result['methods'] if e['method']==m['method'])
                            status,exception,message=event['status'],event.get('exception'),event.get('message')
                            milliseconds,launch=event.get('milliseconds'),event.get('launch')
                            if status=='ignored':
                                status='not-run'
                                message='JUnit ignored/assumption: '+(message or '')
                    assert status in STATUSES
                    point_rows.append(dict(record=f'Lang-{bug}',bug_id=bug,cut_class=m['cut_class'],population=m['population'],
                        own_class=m['own_class'],round=m['round'],technique=m['technique'],**{'class':m['test_class']},
                        method=m['method'],timepoint=point['id'],step=point['step'],days_after_t=point['days_after_t'],
                        calendar_days_after_t=point['calendar_days_after_t'],status=status,exception=exception,
                        message_head=(message or '')[:500],milliseconds=milliseconds,launch=launch,
                        compile_categories=categories,source_file=m['file'],source_sha256=m['source_sha256'],
                        body_hash=m.get('body_hash',''),evidence_directory=log))
                rows.extend(point_rows)
                key=f'Lang-{bug}/{point["id"]}'
                if key not in state['completed_points']:
                    state['completed_points'].append(key)
                state['method_timepoint_rows']=len(rows)
                save(state_path,state)
                print(f'{key}: {dict(Counter(r["status"] for r in point_rows))}; {len(state["completed_points"])}/105 points',flush=True)
        with open('results/p2-survival-methods.csv','w',newline='') as stream:
            writer=csv.DictWriter(stream,list(rows[0]),lineterminator='\n');writer.writeheader();writer.writerows(rows)
        save('results/p2-survival-files.json',dict(files=files,complete=True))
        state.update(complete=True,completed_at=datetime.now(timezone.utc).isoformat(),
            counts=dict(Counter(r['status'] for r in rows)),file_timepoint_compilations=len(files),
            compile_fail_files=sum(not f['compile']['compile_ok'] for f in files),
            file_timepoint_runs=sum(f['compile']['compile_ok'] for f in files))
    finally:
        session['wall_seconds']=round(time.monotonic()-started,3)
        session['ended_at']=datetime.now(timezone.utc).isoformat()
        state['wall_seconds']=round(sum(s.get('wall_seconds',0) for s in state['sessions']),3)
        save(state_path,state)


if __name__=='__main__':
    with Path('results/.p2-survival.lock').open('w') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        main()
