"""Independent reconciliation of patch evidence, classification and every summary view."""
from collections import Counter, defaultdict
import csv
from decimal import Decimal
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET
from patch_audit import parse_patch, changed_lines, matches

STATUSES=['pass','fail','error','timeout','not-run','compile-fail','absent']
POPS=['dev','dev-own','llm']


def load(path):return json.loads(Path(path).read_text())
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def identity(r):return (int(r['bug_id']),r['population'],str(r.get('round') or ''),r.get('technique') or '',r.get('class',r.get('test_class')),r['method'])
def select(r,pop):return r['population']==('dev' if pop=='dev-own' else pop) and (pop!='dev-own' or r['own_class'])


def main():
    started=time.monotonic()
    policy=load('results/p2-approved-policy.json')
    assert policy['step7_patch_lines']=='actual fixed-version lines'
    timeline=load('results/p2-timeline.json');population=load('results/p2-population.json')
    records={r['bug_id']:r for r in population['records']}
    points={(r['bug_id'],p['id']):dict(p,days_bin=0.0 if p['step']==1 else p['days_after_t'])
            for r in timeline['records'] for p in r['timepoints']}
    with open('results/p2-survival-methods.csv',newline='') as stream:rows=list(csv.DictReader(stream))
    for r in rows:
        r['bug_id']=int(r['bug_id']);r['step']=int(r['step']);r['own_class']=r['own_class']=='True'
    # The approved direction and subsequent analysis must not change frozen populations or measurements.
    preserved=['results/p2-population.json','results/p2-survival-methods.csv','results/p2-generation-rounds.json','results/p2-dedup.csv']
    for path in preserved:
        saved=subprocess.check_output(['git','show','ee1ac94:'+path])
        assert saved==Path(path).read_bytes(),path
    for f in load('results/p2-generation-rounds.json')['files']:
        if f['file']:assert sha(f['file'])==f['extracted_sha256']
    for r in load('results/p2-dev-baseline.json')['records']:
        for f in r['classes']:
            if not f['missing']:assert sha(f['file'])==f['sha256']
    reversed_audit=load('results/p2-patch-audit/reversed-audit.json')
    patched=load('results/p2-fixed-patched-lines.json')
    patch_checks=[]
    for r in reversed_audit['records']:
        bug=r['bug_id'];assert sha(r['original_patch'])==r['original_sha256']
        assert sha(r['reversed_patch'])==r['reversed_sha256']
        files=parse_patch(Path(r['reversed_patch']).read_text())
        assert all(matches(f,Path('d4j')/f'Lang-{bug}b','old') and matches(f,Path('d4j')/f'Lang-{bug}f','new') for f in files)
        cut=next(f for f in files if f['new_path']==patched[str(bug)]['source_path'])
        assert changed_lines(cut,'new')==patched[str(bug)]['lines']
        # Git independently checks application in each direction without modifying either checkout.
        path=str(Path(r['reversed_patch']).resolve())
        for kind,reverse in [('b',False),('f',True)]:
            command=['git','apply','--check',*(['--reverse'] if reverse else []),path]
            check=subprocess.run(command,cwd=Path('d4j')/f'Lang-{bug}{kind}',capture_output=True,text=True)
            assert check.returncode==0,(bug,kind,check.stderr)
        patch_checks.append(bug)
    deletion=parse_patch('--- a/X.java\n+++ b/X.java\n@@ -4,3 +4,2 @@\n keep\n-removed\n next\n')[0]
    assert changed_lines(deletion,'new')==[5]
    classification=load('results/p2-fixed-classification.json')
    eligible={identity(r):r for r in rows if r['step']==1 and r['status'] not in {'pass','compile-fail','absent'}}
    assert set(eligible)=={identity(r) for r in classification['methods']}
    assert len(eligible)==len(classification['methods'])
    methods={identity(m):m for m in population['methods']}
    changed_status=[]
    for r in classification['methods']:
        original=eligible[identity(r)];assert original['status']==r['status_at_f']
        assert identity(r) in methods and not methods[identity(r)].get('is_duplicate',False)
        tree=ET.parse(r['xml_file'])
        sources=tree.findall('.//sourcefile');assert len(sources)==1
        executed={int(line.attrib['nr']) for line in sources[0].findall('line') if int(line.attrib['ci'])>0}
        assert executed==set(r['executed_lines']) and len(executed)==r['executed_line_count']
        intersection=executed & set(patched[str(r['bug_id'])]['lines'])
        assert intersection==set(r['intersecting_lines'])
        assert r['classification']==('patch-related' if intersection else 'unrelated')
        summary=load(Path(r['xml_file']).parent/'run.summary.json')
        assert summary['selected_methods']==[r['method']] and len(summary['methods'])==1
        assert summary['methods'][0]['status']==r['coverage_status']
        assert summary['cwd']==f'/work/d4j/Lang-{r["bug_id"]}f'
        for launch in summary['launches']:
            command=launch['command'];excluded=set(command[command.index('--exclude')+1].split(','))
            assert excluded==set(summary['all_listed_methods'])-{r['method']}
            events=[json.loads(s) for s in launch['stdout'].splitlines() if s.startswith('{"method":')]
            assert len(events)==1 and events[0]['method']==r['method']
        if r['coverage_status']!=r['status_at_f']:changed_status.append(identity(r))
    with open('results/p2-fixed-classification.csv',newline='') as stream:classification_csv=list(csv.DictReader(stream))
    assert {identity(r) for r in classification_csv}==set(eligible)
    for r in classification_csv:
        original=next(m for m in classification['methods'] if identity(m)==identity(r))
        for key,value in r.items():
            expected=original.get(key)
            if isinstance(expected,list):expected='|'.join(map(str,expected))
            assert value==('' if expected is None else str(expected)),(identity(r),key)
    matrix=load('results/p2-survival-matrix.json')
    dedup=load('results/p2-dedup.json')['methods']
    raw=Counter((r['bug_id'],r['technique']) for r in dedup if r['passed_at_t'])
    removed=Counter((r['bug_id'],r['technique']) for r in dedup if r['is_duplicate'])
    files=load('results/p2-survival-files.json')['files']
    count_views=0
    for view in ['survival_by_record','survival_pooled_step','survival_by_record_days','survival_pooled_days','per_technique','fixed_classification']:
        for report in matrix[view]:
            keys=[key for key,p in points.items() if
                (report.get('bug_id') is None or report['bug_id']==key[0]) and
                ('timepoint' not in report or report['timepoint']==key[1]) and
                ('step' not in report or report['step']==p['step']) and
                (view!='fixed_classification' or p['step']==1) and
                ('days_bin' not in report or report['days_bin']==p['days_bin'])]
            group=[r for r in rows if (r['bug_id'],r['timepoint']) in keys and select(r,report['population']) and
                (report.get('technique') is None or report['technique']==r['technique'])]
            counts=Counter(r['status'] for r in group)
            assert report['record_timepoint_pairs']==len(keys)
            assert report['population_total']==len(group)
            assert all(report[s]==counts[s] for s in STATUSES)
            denominator=len(group)-counts['absent'];assert report['denominator']==denominator
            assert report['survival'] is None if not denominator else math.isclose(report['survival'],counts['pass']/denominator)
            if report['population']=='llm':
                expected_raw=sum(n for bug,_ in keys for (b,t),n in raw.items() if bug==b and (report.get('technique') is None or report['technique']==t))
                expected_removed=sum(n for bug,_ in keys for (b,t),n in removed.items() if bug==b and (report.get('technique') is None or report['technique']==t))
                assert report['raw_llm']==expected_raw and report['duplicates_removed']==expected_removed
                assert expected_raw-expected_removed==len(group)
            categories=Counter(c for r in group if r['status']=='compile-fail' for c in r['compile_categories'].split('|') if c)
            assert dict(categories)==report['compile_category_affected_methods']
            sources={(r['bug_id'],r['timepoint'],r['source_file']) for r in group if r['status']=='compile-fail'}
            diagnostics=Counter()
            for f in files:
                if (f['bug_id'],f['timepoint'],f['input']['file']) in sources:diagnostics.update(f['compile']['categories'])
            assert dict(diagnostics)==report['compile_category_diagnostics']
            if view=='fixed_classification':
                cs=Counter(r['classification'] for r in classification['methods'] if select(r,report['population']) and
                    (report['bug_id'] is None or r['bug_id']==report['bug_id']))
                assert report['patch_related']==cs['patch-related'] and report['unrelated']==cs['unrelated']
                assert report['eligible_failures']==len(group)-counts['pass']-counts['compile-fail']
            count_views+=1
    pairs=[];exclusions=[]
    for key,point in points.items():
        for pop in ['dev','dev-own']:
            group=[r for r in rows if (r['bug_id'],r['timepoint'])==key and select(r,pop)]
            llm=[r for r in rows if (r['bug_id'],r['timepoint'])==key and r['population']=='llm']
            if point['status']=='absent' or not group or not llm:
                exclusions.append((key,pop));continue
            pairs.append((key,pop,all(r['status']=='pass' for r in group),all(r['status']=='pass' for r in llm)))
    assert len(pairs)==len(matrix['class_pairs']) and len(exclusions)==len(matrix['class_pairs_excluded'])
    assert not any(key[0]==57 for key,_,_,_ in pairs)
    assert not any(pop=='dev-own' and key[0] in [6,17,28,57] for key,pop,_,_ in pairs)
    expected_disagreements={(b,t,p) for ((b,t),p,d,l) in pairs if d and not l}
    assert {(r['bug_id'],r['timepoint'],r['population']) for r in matrix['dev_all_llm_nonpass']}==expected_disagreements
    for report in matrix['class_2x2']:
        group=[r for r in pairs if r[1]==report['population']]
        counts=Counter((r[2],r[3]) for r in group)
        assert report['pairs']==len(group)
        for field,key in [('both_all_pass',(True,True)),('developer_all_llm_some',(True,False)),('developer_some_llm_all',(False,True)),('both_some_nonpass',(False,False))]:
            assert report[field]==counts[key]
        for field,source in [('raw_llm','L_r'),('duplicates_removed','duplicates_removed'),('unique_llm','L_r_unique')]:
            assert report[field]==sum(records[key[0]][source] for key,_,_,_ in group)
    assert matrix['counts_matched']==population['records']
    assert all(r['L_r_unique']>=r['D_r'] and r['target_reached'] for r in matrix['counts_matched'])
    assert len(matrix['coverage_rerun_status_changes'])==len(changed_status)
    ledger=load('results/p2-generation-rounds.json')
    cost=sum(Decimal(str(r['reported_cost_usd'])) for r in ledger['rounds'])
    assert cost==Decimal(str(ledger['reported_cost_usd']))
    # Markdown table syntax: all rows have the expected number of cells.
    tables=0
    for name in ['results/p2-survival-summary.md']:
        text=Path(name).read_text();width=None
        for line in text.splitlines():
            if line.startswith('|'):
                if width is None:width=line.count('|');tables+=1
                assert line.count('|')==width,(name,line)
            else:width=None
        assert policy['lang57_developer_note'] in text
    key=os.environ.get('OPENROUTER_API_KEY','').encode();assert key
    scanned=0
    for root in ['scripts','docs','results','runs','generated','devtests','survival','prompts']:
        for path in Path(root).rglob('*'):
            if path.is_file() and path.suffix not in {'.class','.pyc'}:
                assert key not in path.read_bytes(),'Credential in artifact (value withheld)'
                scanned+=1
    report=dict(complete=True,wall_seconds=round(time.monotonic()-started,3),
        patch_records_verified=sorted(patch_checks),git_apply_checks=2*len(patch_checks),deletion_only_position_check=True,
        classification_methods=len(eligible),classification_counts=classification['counts'],
        single_method_selection_verified=True,coverage_rerun_status_changes=[list(r) for r in changed_status],
        aggregate_metric_rows_recomputed=count_views,class_pairs_recomputed=len(pairs),
        excluded_class_pairs_recomputed=len(exclusions),markdown_tables_checked=tables,
        frozen_artifacts_unchanged=preserved,all_test_source_hashes_unchanged=True,
        generation_calls=sum(r['calls'] for r in ledger['rounds']),reported_generation_cost_usd=str(cost),
        credential_scan_files=scanned,credential_absent=True)
    Path('results/p2-validation-c.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))


if __name__=='__main__':main()
