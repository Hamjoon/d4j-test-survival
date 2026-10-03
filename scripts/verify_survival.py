"""Audit every method/timepoint and actual Java selection against frozen populations."""
from collections import Counter
import os
from pathlib import Path
from aggregate import read_rows
from generate_rounds import load, save, sha
from run_class import events
from survival import STATUSES


def identity(row):
    return (int(row['bug_id']),row['population'],str(row.get('round') or ''),str(row.get('technique') or ''),
            row.get('test_class',row.get('class')),row['method'])


def main():
    progress=load('results/p2-survival-progress.json');assert progress['complete']
    population=load('results/p2-population.json');timeline=load('results/p2-timeline.json')
    assert sha('results/p2-population.json')==progress['population_sha256']
    rows=read_rows();actual={(identity(r),r['timepoint']) for r in rows}
    methods={identity(m):m for m in population['methods']}
    points={(r['bug_id'],p['id']):p for r in timeline['records'] for p in r['timepoints']}
    expected={(identity(m),p['id']) for r in timeline['records'] for p in r['timepoints']
              for m in population['methods'] if m['bug_id']==r['bug_id']}
    assert actual==expected and len(rows)==len(actual)
    for m in population['methods']:assert sha(m['file'])==m['source_sha256']
    for r in rows:
        base=methods[identity(r)];p=points[r['bug_id'],r['timepoint']]
        assert r['own_class']==base['own_class'] and r['status'] in STATUSES
        assert (r['status']=='absent')==(p['status']=='absent')
        assert r['days_after_t']==p['days_after_t'] and r['step']==p['step']
        if r['population']=='llm':assert r['body_hash']==base['body_hash']
    dedup=load('results/p2-dedup.json')['methods']
    duplicate_ids={(r['bug_id'],'llm',str(r['round']),r['technique'],r['class'],r['method']) for r in dedup if r['is_duplicate']}
    assert not duplicate_ids & {identity(r) for r in rows}
    files=load('results/p2-survival-files.json')['files']
    executions=0;exclusions=0;missing=[]
    for f in files:
        selected={m['method'] for m in population['methods'] if m['file']==f['input']['file']}
        assert set(f['input']['selected_methods'])==selected
        if not f['compile']['compile_ok']:continue
        summary=load(Path(f['run_file']).with_suffix('.summary.json'))
        assert summary['selected_methods']==sorted(selected)
        assert summary['cwd']==f'/work/d4j/{f["timepoint"]}'
        assert summary['classpath'].split(':')[0]=='/work/'+f['classes']
        measured=set()
        nonpopulation=set(summary['all_listed_methods'])-selected
        assert set(summary['excluded_methods'])==nonpopulation
        for launch in summary['launches']:
            command=launch['command']
            excluded=set(command[command.index('--exclude')+1].split(',')) if '--exclude' in command else set()
            assert excluded==nonpopulation|measured
            for event in events(launch['stdout']):
                assert event['method'] in selected and event['method'] not in measured
                measured.add(event['method']);executions+=1
        absent_from_listing=selected-set(summary['all_listed_methods'])
        assert measured<=selected
        for m in summary['methods']:
            if m['method'] in absent_from_listing:
                assert m['status']=='not-run' and m['exception']=='JUnitMethodMissing'
                missing.append(dict(bug_id=f['bug_id'],timepoint=f['timepoint'],**m))
        if not summary['list_error']:
            assert measured|absent_from_listing==selected
        exclusions+=len(nonpopulation)
        file_rows=[r for r in rows if r['bug_id']==f['bug_id'] and r['timepoint']==f['timepoint'] and r['source_file']==f['input']['file']]
        assert {r['method'] for r in file_rows}==selected
        assert {r['method']:r['status'] for r in file_rows}=={m['method']:('not-run' if m['status']=='ignored' else m['status']) for m in summary['methods']}
    key=os.environ.get('OPENROUTER_API_KEY','').encode()
    assert key, 'Use compose env_file for the non-disclosing credential scan'
    scanned=0
    for root in ['scripts','docs','results','runs','generated','devtests','survival','prompts']:
        for path in Path(root).rglob('*'):
            if path.is_file() and path.suffix not in {'.class','.pyc'}:
                assert key not in path.read_bytes(), 'Credential found in artifact (value withheld)'
                scanned+=1
    report=dict(complete=True,method_timepoint_rows=len(rows),record_timepoints=len(points),
        actual_method_executions=executions,nonpopulation_exclusions_across_files=exclusions,
        duplicate_population_rows=0,duplicate_method_executions=0,source_hashes_unchanged=True,
        full_identity_cross_product_verified=True,classpath_and_working_directories_verified=True,
        counts=dict(Counter(r['status'] for r in rows)),missing_inherited_methods=missing,
        credential_scan_files=scanned,credential_absent=True)
    save('results/p2-survival-validation.json',report)
    print({k:v for k,v in report.items() if k!='missing_inherited_methods'})


if __name__=='__main__':main()
