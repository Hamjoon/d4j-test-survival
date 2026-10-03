"""Check the added bins directly against saved CSV rows; execute no experiment methods."""
from bisect import bisect_left
from collections import Counter, defaultdict
import csv
from decimal import Decimal
import json
import math
from pathlib import Path
import subprocess

LABELS=['0','1-30','31-180','181-365','366-730','731+']
POPS=['dev','dev-own','llm']
STATUSES=['pass','fail','error','timeout','not-run','compile-fail','absent']
BASELINE='64937b3'  # Completed Step 9, before the requested day-range addition.


def bin_for(row):
    if int(row['step'])==1:return '0'
    days=Decimal(row['days_after_t']);assert days>0
    return LABELS[1+bisect_left([Decimal(n) for n in [30,180,365,730]],days)]


def main():
    matrix=json.loads(Path('results/p2-survival-matrix.json').read_text())
    with open('results/p2-survival-methods.csv',newline='') as stream:rows=list(csv.DictReader(stream))
    reports=matrix['survival_pooled_day_bins']
    assert [(r['day_bin'],r['population']) for r in reports]==[(b,p) for b in LABELS for p in POPS]
    groups=defaultdict(list);points=defaultdict(set)
    for row in rows:
        b=bin_for(row);groups[b,row['population']].append(row)
        if row['population']=='dev' and row['own_class']=='True':groups[b,'dev-own'].append(row)
        points[b].add((int(row['bug_id']),row['timepoint']))
    population=json.loads(Path('results/p2-population.json').read_text())
    records={r['bug_id']:r for r in population['records']}
    for report in reports:
        key=report['day_bin'],report['population'];group=groups[key]
        counts=Counter(r['status'] for r in group)
        assert all(report[s]==counts[s] for s in STATUSES),key
        assert report['population_total']==len(group)
        assert report['record_timepoint_pairs']==len(points[key[0]])
        present=len(group)-counts['absent'];assert report['denominator']==present
        if present:
            assert math.isclose(report['survival'],counts['pass']/present)
            assert math.isclose(report['survival_percent'],100*counts['pass']/present)
        else:
            assert report['survival'] is None and report['survival_percent'] is None
        if report['population']=='llm':
            raw=sum(records[b]['L_r'] for b,_ in points[key[0]])
            removed=sum(records[b]['duplicates_removed'] for b,_ in points[key[0]])
            assert report['raw_llm']==raw and report['duplicates_removed']==removed
            assert raw-removed==len(group)
    for pop in POPS:
        original=[r for r in rows if r['population']==('dev' if pop=='dev-own' else pop)
                  and (pop!='dev-own' or r['own_class']=='True')]
        assert sum(len(groups[b,pop]) for b in LABELS)==len(original)
        for status in STATUSES:
            assert sum(r[status] for r in reports if r['population']==pop)==sum(r['status']==status for r in original)
    # Persisted per-day and other numeric views must remain unchanged by this addition.
    old=json.loads(subprocess.check_output(['git','show',BASELINE+':results/p2-survival-matrix.json']))
    for key,value in old.items():
        if key=='semantics':
            assert all(matrix[key][k]==v for k,v in value.items())
        elif key!='wall_seconds':assert matrix[key]==value,key
    original_summary=subprocess.check_output(['git','show',BASELINE+':results/p2-survival-summary.md']).decode()
    summary=Path('results/p2-survival-summary.md').read_text()
    begin=summary.index('### Pooled by day range\n')
    end=summary.index('## Failure kinds\n',begin)
    assert summary[:begin]+summary[end:]==original_summary
    assert summary in Path('docs/handover-part2-c.md').read_text()
    immutable=['results/p2-survival-methods.csv','results/p2-population.json','results/p2-dedup.csv',
               'results/p2-generation-rounds.json','results/p2-fixed-classification.json']
    for name in immutable:
        assert Path(name).read_bytes()==subprocess.check_output(['git','show',BASELINE+':'+name])
    cases={'0.012211':'1-30','30':'1-30','30.000001':'31-180','180':'31-180','180.000001':'181-365',
           '365':'181-365','365.000001':'366-730','730':'366-730','730.000001':'731+'}
    # Read only the bin function from its AST, avoiding imports of any execution modules.
    import ast
    module=ast.parse(Path('scripts/aggregate.py').read_text())
    function=next(node for node in module.body if isinstance(node,ast.FunctionDef) and node.name=='day_range')
    scope={'DAY_RANGES':[(label,bound) for label,bound in zip(LABELS[1:],[30,180,365,730,None])]}
    exec(compile(ast.Module(body=[function],type_ignores=[]),'day_range','exec'),scope)
    for value,expected in cases.items():assert scope['day_range'](2,Decimal(value))==expected
    assert scope['day_range'](1,Decimal('35.229722'))=='0'
    report=dict(complete=True,baseline_commit=BASELINE,bin_order=LABELS,populations=POPS,aggregate_rows_checked=len(reports),
        original_method_timepoint_rows=len(rows),exact_decimal_assignment_checked=True,
        boundary_cases_checked=len(cases)+1,all_status_counts_conserved=True,
        existing_summary_and_matrix_views_unchanged=True,immutable_input_files=immutable,
        experiment_runs=0,api_calls=0)
    Path('results/p2-day-bin-validation.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))


if __name__=='__main__':main()
