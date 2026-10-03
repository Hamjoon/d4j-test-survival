"""Step 7: single-method JaCoCo coverage intersected with approved patch lines."""
from collections import Counter
import csv
from datetime import datetime, timezone
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from aggregate import read_rows
from generate_rounds import load, save, sha
from patch_audit import parse_patch, changed_lines, matches
from p2_baseline import JARS
from run_class import run_class


def reverse_patch(text):
    lines=text.splitlines();out=[];index=0
    while index<len(lines):
        line=lines[index]
        if line.startswith('--- '):
            assert lines[index+1].startswith('+++ ')
            out += ['--- '+lines[index+1][4:].replace('b/','a/',1),'+++ '+line[4:].replace('a/','b/',1)]
            index+=2;continue
        if line.startswith('@@ '):
            m=re.match(r'@@ -(\d+(?:,\d+)?) \+(\d+(?:,\d+)?) @@(.*)',line)
            line=f'@@ -{m[2]} +{m[1]} @@{m[3]}'
        elif line.startswith('+'):line='-'+line[1:]
        elif line.startswith('-'):line='+'+line[1:]
        elif line.startswith('index '):
            m=re.match(r'index (\w+)\.\.(\w+)(.*)',line)
            line=f'index {m[2]}..{m[1]}{m[3]}'
        out.append(line);index+=1
    return '\n'.join(out)+'\n'


def main():
    started=time.monotonic()
    policy=load('results/p2-approved-policy.json')
    assert policy.get('step7_patch_lines') == 'actual fixed-version lines', 'Approved fixed-version mapping required'
    assert load('results/p2-survival-progress.json')['complete']
    rows=read_rows()
    candidates=[r for r in rows if r['step']==1 and r['status'] not in {'pass','compile-fail','absent'}]
    records={r['bug_id']:r for r in load('results/lang-records.json')}
    timeline=load('results/p2-timeline.json')
    audits={r['bug_id']:r for r in load('results/p2-patch-audit/audit.json')['records']}
    patchsets={}; reversed_audits=[]
    for bug,record in records.items():
        audit=audits[bug]
        assert audit['orientation']=='fixed-to-buggy'
        assert sha(audit['patch']) == audit['sha256']
        original=Path(audit['patch']).read_text()
        patch=reverse_patch(original)
        path=Path('results/p2-patch-audit')/f'{bug}.buggy-to-fixed.patch'
        if path.exists(): assert path.read_text()==patch
        else: path.write_text(patch)
        files=parse_patch(patch)
        comparisons=[dict(old_path=f['old_path'],new_path=f['new_path'],
            old_matches_buggy=matches(f,Path('d4j')/f'Lang-{bug}b','old'),
            new_matches_fixed=matches(f,Path('d4j')/f'Lang-{bug}f','new')) for f in files]
        assert all(f['old_matches_buggy'] and f['new_matches_fixed'] for f in comparisons)
        reversed_audits.append(dict(bug_id=bug,original_patch=audit['patch'],original_sha256=audit['sha256'],
            reversed_patch=str(path),reversed_sha256=sha(path),orientation='buggy-to-fixed',files=comparisons))
        suffix=record['package'].replace('.','/')+'/'+record['class']+'.java'
        cut=[f for f in files if f['new_path'].endswith(suffix)]
        assert len(cut)==1
        patchsets[bug]=dict(source_path=cut[0]['new_path'],lines=changed_lines(cut[0],'new'),rule=policy['step7_patch_lines'])
    save('results/p2-fixed-patched-lines.json',patchsets)
    save('results/p2-patch-audit/reversed-audit.json',dict(complete=True,records=reversed_audits,
        step7_patch_lines=policy['step7_patch_lines'],deletion_only_rule='first deletion position in fixed-version coordinates'))
    methods=[]
    for index,row in enumerate(candidates,1):
        bug=row['bug_id'];record=records[bug];point=f'Lang-{bug}f'
        evaluation=load(Path(row['evidence_directory'])/'evaluation.json')
        assert evaluation['compile']['compile_ok']
        assert sha(row['source_file'])==row['source_sha256']
        folder=Path(row['evidence_directory'])/'classification'/row['method']
        folder.mkdir(parents=True,exist_ok=True)
        evidence=dict(bug_id=bug,population=row['population'],own_class=row['own_class'],round=row['round'],
            technique=row['technique'],**{'class':row['class']},method=row['method'],status_at_f=row['status'],
            exception=row['exception'],source_sha256=row['source_sha256'],patch_rule=policy['step7_patch_lines'])
        result_path=folder/'classification.json'
        if result_path.exists():
            result=load(result_path)
            assert result['input']==evidence
        else:
            exec_file=(folder/'jacoco.exec').resolve()
            cp=(Path('results/d4j-export')/(point+'.cp.test')).read_text().strip()
            output=folder/'run.jsonl'
            summary_path=output.with_suffix('.summary.json')
            summary=load(summary_path) if summary_path.exists() else run_class(row['class'],
                f'{evaluation["classes"]}:tools/runner:{cp}:{JARS}',output,
                java_options=[f'-javaagent:{Path("tools/jacocoagent.jar").resolve()}=destfile={exec_file},append=true,includes={record["package"]}.*'],
                working_directory=Path('d4j')/point,include_methods=[row['method']])
            assert summary['selected_methods']==[row['method']] and len(summary['methods'])==1
            assert exec_file.exists(),f'Coverage missing: {folder}'
            version=timeline['versions'][point]
            classroot=Path('d4j')/point/version['exports']['dir.bin.classes']
            cut=classroot/record['package'].replace('.','/')/(record['class']+'.class')
            assert cut.exists()
            classfiles=[cut,*sorted(cut.parent.glob(record['class']+'$*.class'))]
            xml=folder/'jacoco.xml'
            command=['java','-jar','tools/jacococli.jar','report',str(exec_file)]
            for c in classfiles:command+=['--classfiles',str(c)]
            command+=['--sourcefiles',str(Path('d4j')/point/version['exports']['dir.src.classes']),'--xml',str(xml)]
            result_process=subprocess.run(command,capture_output=True,text=True)
            (folder/'jacoco-report.out').write_text(result_process.stdout)
            (folder/'jacoco-report.err').write_text(result_process.stderr)
            save(folder/'jacoco-report.json',dict(command=command,exit_status=result_process.returncode))
            assert result_process.returncode==0 and 'does not match' not in result_process.stderr+result_process.stdout
            tree=ET.parse(xml)
            source=tree.find(f'.//package[@name="{record["package"].replace(".","/")}"]/sourcefile[@name="{record["class"]}.java"]')
            assert source is not None
            executed=sorted(int(line.attrib['nr']) for line in source.findall('line') if int(line.attrib['ci'])>0)
            intersection=sorted(set(executed)&set(patchsets[bug]['lines']))
            result=dict(input=evidence,classification='patch-related' if intersection else 'unrelated',
                executed_lines=executed,executed_line_count=len(executed),patched_lines=patchsets[bug]['lines'],
                intersecting_lines=intersection,coverage_status=summary['methods'][0]['status'],
                coverage_exception=summary['methods'][0].get('exception'),xml_file=str(xml),exec_file=str(folder/'jacoco.exec'))
            save(result_path,result)
        assert sha(row['source_file'])==row['source_sha256']
        methods.append(dict(**evidence,**{k:v for k,v in result.items() if k!='input'}))
        print(f'{index}/{len(candidates)} Lang-{bug} {row["population"]} {row["method"]}: {result["classification"]}; '
              f'intersection={result["intersecting_lines"]}',flush=True)
    columns=['bug_id','population','own_class','round','technique','class','method','status_at_f','exception',
             'classification','executed_line_count','intersecting_lines','patched_lines','coverage_status','coverage_exception','xml_file','exec_file']
    with open('results/p2-fixed-classification.csv','w',newline='') as stream:
        writer=csv.DictWriter(stream,columns,lineterminator='\n');writer.writeheader()
        for row in methods:writer.writerow({c:'|'.join(map(str,row[c])) if isinstance(row.get(c),list) else row.get(c) for c in columns})
    report=dict(complete=True,methods=methods,patch_rule=policy['step7_patch_lines'],
        coverage_scope='CUT source file, including nested classes compiled from that file',
        counts=dict(Counter(r['classification'] for r in methods)),wall_seconds=round(time.monotonic()-started,3),
        completed_at=datetime.now(timezone.utc).isoformat())
    save('results/p2-fixed-classification.json',report)
    print(report['counts'],flush=True)


if __name__=='__main__':main()
