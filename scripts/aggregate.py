"""Step 8: method survival, reporting-only dev-own, and raw/unique LLM context."""
from collections import Counter, defaultdict
import csv
import json
from pathlib import Path
import time
from generate_rounds import load, save
from survival import STATUSES
from extract_v2 import TECHS

POPS=['dev','dev-own','llm']
DAY_RANGES=[('1-30',30),('31-180',180),('181-365',365),('366-730',730),('731+',None)]


def day_range(step,days):
    if step==1:
        return '0'
    assert days>0, 'Later buggy versions must have a positive exact day gap'
    for label,upper in DAY_RANGES:
        if upper is None or days<=upper:
            return label


def read_rows():
    with open('results/p2-survival-methods.csv',newline='') as stream:
        rows=list(csv.DictReader(stream))
    for r in rows:
        r['bug_id'],r['step']=int(r['bug_id']),int(r['step'])
        r['days_after_t']=float(r['days_after_t'])
        r['own_class']=r['own_class']=='True'
    return rows


def selected(row,pop):
    return row['population']==('dev' if pop=='dev-own' else pop) and (pop!='dev-own' or row['own_class'])


def table(headers,rows):
    def cell(value):
        if value is None: return '—'
        return str(value).replace('|',' / ').replace('\n',' ')
    return ['| '+' | '.join(map(cell,headers))+' |','|'+'|'.join('---' for _ in headers)+'|',
            *['| '+' | '.join(map(cell,row))+' |' for row in rows]]


def main():
    started=time.monotonic()
    assert load('results/p2-survival-progress.json')['complete']
    assert load('results/p2-fixed-classification.json')['complete']
    rows=read_rows()
    population=load('results/p2-population.json')
    records={r['bug_id']:r for r in population['records']}
    timeline=load('results/p2-timeline.json')
    files=load('results/p2-survival-files.json')['files']
    classified=load('results/p2-fixed-classification.json')['methods']
    coverage_changes=[dict(r,raw_llm=records[r['bug_id']]['L_r'],
        duplicates_removed=records[r['bug_id']]['duplicates_removed'],
        unique_llm=records[r['bug_id']]['L_r_unique']) for r in classified if r['coverage_status']!=r['status_at_f']]
    points={(r['bug_id'],p['id']):dict(p,bug_id=r['bug_id'],days_bin=0.0 if p['step']==1 else p['days_after_t'])
            for r in timeline['records'] for p in r['timepoints']}
    bypoint=defaultdict(list)
    for row in rows: bypoint[(row['bug_id'],row['timepoint'])].append(row)
    def metric(keys,pop,tech=None):
        group=[r for key in keys for r in bypoint[key] if selected(r,pop) and (tech is None or r['technique']==tech)]
        counts=Counter(r['status'] for r in group)
        total=len(group);denominator=total-counts['absent']
        raw=removed=None
        if pop=='llm':
            raw=sum(records[b]['L_r'] if tech is None else records[b]['technique_raw_counts'][tech] for b,_ in keys)
            removed=sum(records[b]['duplicates_removed'] if tech is None else records[b]['technique_duplicates_removed'][tech] for b,_ in keys)
            assert raw-removed==total
        method_categories=Counter(category for r in group if r['status']=='compile-fail' for category in r['compile_categories'].split('|') if category)
        diagnostic_categories=Counter()
        affected_sources={(r['bug_id'],r['timepoint'],r['source_file']) for r in group if r['status']=='compile-fail'}
        for f in files:
            if (f['bug_id'],f['timepoint'],f['input']['file']) in affected_sources:
                diagnostic_categories.update(f['compile']['categories'])
        return dict(population=pop,technique=tech,record_timepoint_pairs=len(keys),raw_llm=raw,
            duplicates_removed=removed,population_total=total,denominator=denominator,
            **{s:counts[s] for s in STATUSES},survival=None if not denominator else counts['pass']/denominator,
            compile_category_affected_methods=dict(method_categories),compile_category_diagnostics=dict(diagnostic_categories))
    per_record=[]
    for key,p in points.items():
        for pop in POPS:
            per_record.append(dict(record=f'Lang-{key[0]}',bug_id=key[0],timepoint=key[1],step=p['step'],
                days_after_t=p['days_after_t'],days_bin=p['days_bin'],**metric([key],pop)))
    def grouped(field):
        groups=defaultdict(list)
        for key,p in points.items(): groups[p[field]].append(key)
        return [dict(**{field:value},**metric(keys,pop)) for value,keys in sorted(groups.items()) for pop in POPS]
    pooled_step,pooled_days=grouped('step'),grouped('days_bin')
    range_groups={label:[] for label in ['0',*[label for label,_ in DAY_RANGES]]}
    for key,p in points.items():
        range_groups[day_range(p['step'],p['days_after_t'])].append(key)
    pooled_day_bins=[]
    for label,keys in range_groups.items():
        for pop in POPS:
            m=metric(keys,pop)
            pooled_day_bins.append(dict(day_bin=label,**m,
                survival_percent=None if m['survival'] is None else 100*m['survival']))
    per_record_days=[]
    groups=defaultdict(list)
    for key,p in points.items():groups[(key[0],p['days_bin'])].append(key)
    for (bug,days),keys in sorted(groups.items()):
        for pop in POPS:
            per_record_days.append(dict(record=f'Lang-{bug}',bug_id=bug,days_bin=days,
                timepoints=[k[1] for k in keys],**metric(keys,pop)))
    fixed=[]
    for bug in [*records,None]:
        keys=[k for k,p in points.items() if p['step']==1 and (bug is None or k[0]==bug)]
        for pop in POPS:
            group=[r for r in classified if selected(r,pop) and (bug is None or r['bug_id']==bug)]
            count=Counter(r['classification'] for r in group)
            m=metric(keys,pop)
            eligible=m['denominator']-m['pass']-m['compile-fail']
            assert sum(count.values())==eligible,(bug,pop,count,eligible)
            fixed.append(dict(record='Pooled' if bug is None else f'Lang-{bug}',bug_id=bug,
                **m,eligible_failures=eligible,patch_related=count['patch-related'],unrelated=count['unrelated']))
    pairs,excluded,disagreements=[],[],[]
    for key,p in points.items():
        llm=metric([key],'llm')
        for pop in ['dev','dev-own']:
            dev=metric([key],pop)
            if not dev['denominator'] or not llm['denominator']:
                excluded.append(dict(record=f'Lang-{key[0]}',timepoint=key[1],population=pop,
                    reason='CUT absent' if p['status']=='absent' else 'empty developer baseline'))
                continue
            d_all=dev['pass']==dev['denominator']; l_all=llm['pass']==llm['denominator']
            pair=dict(record=f'Lang-{key[0]}',bug_id=key[0],timepoint=key[1],population=pop,
                developer_outcome='all pass' if d_all else 'some nonpass',
                llm_outcome='all pass' if l_all else 'some nonpass',
                developer_nonpasses=dev['denominator']-dev['pass'],llm_nonpasses=llm['denominator']-llm['pass'],
                raw_llm=llm['raw_llm'],duplicates_removed=llm['duplicates_removed'],unique_llm=llm['population_total'])
            pairs.append(pair)
            if d_all and not l_all: disagreements.append(pair)
    two_by_two=[]
    for pop in ['dev','dev-own']:
        group=[r for r in pairs if r['population']==pop]
        count=Counter((r['developer_outcome'],r['llm_outcome']) for r in group)
        two_by_two.append(dict(population=pop,pairs=len(group),
            both_all_pass=count['all pass','all pass'],developer_all_llm_some=count['all pass','some nonpass'],
            developer_some_llm_all=count['some nonpass','all pass'],both_some_nonpass=count['some nonpass','some nonpass'],
            raw_llm=sum(r['raw_llm'] for r in group),duplicates_removed=sum(r['duplicates_removed'] for r in group),
            unique_llm=sum(r['unique_llm'] for r in group)))
    techniques=[]
    for step in sorted({p['step'] for p in points.values()}):
        keys=[k for k,p in points.items() if p['step']==step]
        for tech in TECHS: techniques.append(dict(step=step,**metric(keys,'llm',tech)))
    result=dict(complete=True,semantics=dict(llm_population='unique passing methods at t',
        llm_context_columns='raw passing and duplicates removed before survival; repeated once per included record/timepoint in pooled rows',
        absent='excluded from denominator; included in population_total and absent column',
        days='fixed points grouped at 0; other points use exact elapsed 24-hour days rounded to 9 decimals from timeline',
        day_ranges='0 = fixed version regardless of actual gap; nonfixed exact gaps use (0,30], (30,180], (180,365], (365,730], (730,infinity); no rounding',
        dev_own='filter dev rows where own_class is true',
        compile_categories='affected method counts (may overlap); separate raw diagnostic counts per failed file',
        class_2x2='only present CUT pairs with nonempty populations; every status except pass is nonpass'),
        survival_by_record=per_record,survival_pooled_step=pooled_step,survival_by_record_days=per_record_days,
        survival_pooled_days=pooled_days,survival_pooled_day_bins=pooled_day_bins,fixed_classification=fixed,class_2x2=two_by_two,
        class_pairs=pairs,class_pairs_excluded=excluded,dev_all_llm_nonpass=disagreements,
        per_technique=techniques,counts_matched=population['records'],coverage_rerun_status_changes=coverage_changes,
        step7_patch_lines=load('results/p2-approved-policy.json')['step7_patch_lines'])
    out=['# Part 2 survival summary','',
        'The LLM population consists of unique methods that passed at t. Every LLM row shows raw passing methods and exact duplicates removed before survival. '
        'Dev-own filters the existing developer rows. Empty populations are N/A. Absent methods are excluded from survival denominators and reported separately. '
        'Raw/removed/population totals in pooled rows count each included record/timepoint once; they are method-timepoint counts when multiple points are pooled.','',
        'Lang-57 dev and dev-own survival at every time point: **N/A — all developer methods are trigger tests; no developer baseline at t**.','']
    metric_headers=['Population','Raw LLM','Removed duplicates','Population (unique for LLM)','Pass / present population','Survival','fail','error','timeout','not-run','compile-fail','absent']
    def values(r):
        return [r['population'],r['raw_llm'],r['duplicates_removed'],r['population_total'],
            f'{r["pass"]} / {r["denominator"]}' if r['denominator'] else 'N/A',
            'N/A' if r['survival'] is None else f'{r["survival"]:.2%}',
            *[r[s] for s in STATUSES[1:]]]
    out+=['## Survival by time point','']
    for bug in records:
        group=[r for r in per_record if r['bug_id']==bug]
        out += [f'### Lang-{bug}','',*table(['Step','Time point','Actual days',*metric_headers],
            [[r['step'],r['timepoint'],r['days_after_t'],*values(r)] for r in group]),'']
    out+=['### Pooled by step','',*table(['Step',*metric_headers],[[r['step'],*values(r)] for r in pooled_step]),'']
    out+=['## Survival by days after t','',
        'Fixed versions are placed in bin 0 by specification. Later buggy versions use the exact elapsed 24-hour day gap in the timeline (nine decimal places); no calendar-day rounding or additional bin width is applied.','']
    for bug in records:
        group=[r for r in per_record_days if r['bug_id']==bug]
        out += [f'### Lang-{bug}','',*table(['Days bin','Time points',*metric_headers],
            [[r['days_bin'],', '.join(r['timepoints']),*values(r)] for r in group]),'']
    out+=['### Pooled by days','',*table(['Days bin',*metric_headers],[[r['days_bin'],*values(r)] for r in pooled_days]),'']
    out+=['### Pooled by day range','',
        'Bin 0 contains fixed-version rows regardless of their actual gap. For all other time points, the labels 1-30, 31-180, '
        '181-365, 366-730 and 731+ use exact day-gap intervals (0,30], (30,180], (180,365], (365,730] and (730,infinity), respectively. '
        'No day gap is rounded: a positive sub-day gap belongs to 1-30. Each method/timepoint row contributes once; '
        'absent rows are reported beside the present-population denominator. Raw LLM and removed-duplicate counts are summed once per included record/timepoint.','',
        *table(['Days bin',*metric_headers],[[r['day_bin'],*values(r)] for r in pooled_day_bins]),'']
    out+=['## Failure kinds','',
        'Each cell counts affected population methods. Compile categories may overlap for a method when its file has multiple diagnostic categories. '
        'The last column gives raw javac diagnostic counts, counted once per failed file within that population. '
        'Codes follow Part 1: CFS cannot find symbol; PDNE package missing; PAI private access; IT incompatible types; CAM/MAM constructor/method arguments; '
        'DCD duplicate class; PCR public-class filename; SCI instance member from static context; UE unreported exception; VNI uninitialized variable; '
        'WAP weaker access; AR ambiguous reference; USL/UCL unclosed literal; other unmatched diagnostics.','']
    def kinds(r):
        return [r['population'],r['raw_llm'],r['duplicates_removed'],r['population_total'],
                *[r[s] for s in STATUSES[1:]],json.dumps(r['compile_category_affected_methods'],sort_keys=True),
                json.dumps(r['compile_category_diagnostics'],sort_keys=True)]
    headers=['Population','Raw LLM','Removed duplicates','Population (unique for LLM)',*STATUSES[1:],'Compile categories: affected methods','Compile categories: diagnostics']
    out+=table(['Record','Time point',*headers],[[r['record'],r['timepoint'],*kinds(r)] for r in per_record])+['',
        '### Pooled failure kinds by step','']+table(['Step',*headers],[[r['step'],*kinds(r)] for r in pooled_step])+['']
    out+=['## Fixed-version classification','',
        'Only nonpassing methods at the fixed version are classified; compile failures are excluded. '
        'Coverage intersection is an operational classification, not a causal finding. Developer and dev-own results are included for reference. '
        'Patched lines are changed + lines after reversing the original fixed-to-buggy Defects4J patches and verifying both sides against the checkouts.','',
        *table(['Record','Population','Raw LLM','Removed duplicates','Population (unique for LLM)','Eligible nonpasses','Patch-related','Unrelated','Excluded compile-fail'],
            [[r['record'],r['population'],r['raw_llm'],r['duplicates_removed'],r['population_total'],r['eligible_failures'],r['patch_related'],r['unrelated'],r['compile-fail']] for r in fixed]),'']
    out+=['### Status changes during the isolated coverage run','',
        'Step 7 selects methods using the saved Step 6 outcomes. A coverage rerun does not replace those outcomes. '
        'The following method passed in its isolated JaCoCo run after failing in Step 6; it still intersects a patched line under the specified classification rule. '
        'The method contains short sleeps and assertions on their measured durations. The available evidence does not establish why its status changed.','',
        *table(['Record','Technique','Method','LLM raw at t','Removed duplicates','LLM unique at t','Step 6 status','Coverage status','Intersecting fixed lines'],
        [[f'Lang-{r["bug_id"]}',r['technique'],r['method'],r['raw_llm'],r['duplicates_removed'],r['unique_llm'],r['status_at_f'],r['coverage_status'],
          ', '.join(map(str,r['intersecting_lines']))] for r in coverage_changes]),'']
    out+=['## Class-level 2×2','',
        'A pair is one record/timepoint with a present CUT and nonempty baselines. “Some nonpass” includes fail, error, timeout, not-run and compile-fail. '
        'The LLM context columns sum method populations across eligible pairs.','',
        *table(['Developer population','Pairs','Both all pass','Developer all / LLM some','Developer some / LLM all','Both some','LLM raw across pairs','Removed duplicates across pairs','LLM unique across pairs'],
        [[r['population'],r['pairs'],r['both_all_pass'],r['developer_all_llm_some'],r['developer_some_llm_all'],r['both_some_nonpass'],r['raw_llm'],r['duplicates_removed'],r['unique_llm']] for r in two_by_two]),'',
        '*Exclusions: Lang-57 is excluded because all developer methods are trigger tests; no developer baseline at t. '
        'Dev-own also excludes Lang-6, Lang-17 and Lang-28 because their passing own-class baselines are empty. '
        'All absent record/timepoint pairs are excluded. The full exclusion list is in p2-survival-matrix.json.*','',
        '### Developer all pass / LLM some nonpass','',
        *table(['Record','Time point','Developer population','LLM raw','Removed duplicates','LLM unique','LLM nonpasses'],
        [[r['record'],r['timepoint'],r['population'],r['raw_llm'],r['duplicates_removed'],r['unique_llm'],r['llm_nonpasses']] for r in disagreements]),'']
    out+=['## Per-technique survival','',*table(['Step','Technique',*metric_headers],
        [[r['step'],r['technique'],*values(r)] for r in techniques]),'']
    out+=['## Counts matched at t','',
        'The target remains the full developer population D_r; dev-own is reported beside it. Unique counts determine whether the target was reached.','',
        *table(['Record','D_r (dev target)','D_r_own (dev-own)','LLM raw','Removed duplicates','LLM unique','Rounds','Unique target reached'],
        [[f'Lang-{r["bug_id"]}',r['D_r'],r['D_r_own'],r['L_r'],r['duplicates_removed'],r['L_r_unique'],r['rounds_used'],r['target_reached']] for r in records.values()]),'']
    result['wall_seconds']=round(time.monotonic()-started,3)
    save('results/p2-survival-matrix.json',result)
    Path('results/p2-survival-summary.md').write_text('\n'.join(out).rstrip()+'\n')
    print(json.dumps(dict(class_2x2=two_by_two,fixed_pooled=[r for r in fixed if r['bug_id'] is None],wall_seconds=result['wall_seconds']),indent=2))


if __name__=='__main__':main()
