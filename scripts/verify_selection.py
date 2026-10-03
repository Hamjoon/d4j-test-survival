"""Offline regression checks for selection, missing inherited methods and timeout recovery."""
from pathlib import Path
import tempfile
import run_class
from generate_rounds import load, save
from classify_fixed import reverse_patch
from patch_audit import parse_patch, matches


def main():
    original=run_class.invoke
    checks=[]
    try:
        cases=[
            (['keep','missing'],['keep','excluded'],[(0,[('keep','pass')])],{'pass':1,'not-run':1},[{'excluded'}]),
            (['hang','keep'],['hang','excluded','keep'],[(3,[('hang','timeout')]),(0,[('keep','pass')])],
             {'timeout':1,'pass':1},[{'excluded'},{'excluded','hang'}])]
        import json
        for selected,listed,runs,counts,excluded in cases:
            commands=[]
            responses=[dict(exit_status=0,stdout=''.join(json.dumps(dict(method=m))+'\n' for m in listed),stderr='',wall_seconds=0)]
            responses += [dict(exit_status=code,stdout=''.join(json.dumps(dict(method=m,status=s))+'\n' for m,s in methods),
                stderr='',wall_seconds=0) for code,methods in runs]
            def fake(command,*args):
                commands.append(command)
                return dict(command=command,**responses.pop(0))
            run_class.invoke=fake
            with tempfile.TemporaryDirectory() as temp:
                result=run_class.run_class('Fixture','cp',Path(temp)/'run.jsonl',include_methods=selected)
                assert result['counts']==counts
                for command,expected in zip(commands[1:],excluded):
                    assert set(command[command.index('--exclude')+1].split(','))==expected
                assert not responses
            checks.append(dict(selected=selected,counts=counts,launches=len(runs)))
    finally:
        run_class.invoke=original
    audited=[]
    for row in load('results/p2-patch-audit/audit.json')['records']:
        bug=row['bug_id']
        parsed=parse_patch(reverse_patch(Path(row['patch']).read_text()))
        assert all(matches(f,Path('d4j')/f'Lang-{bug}f','new') for f in parsed)
        assert all(matches(f,Path('d4j')/f'Lang-{bug}b','old') for f in parsed)
        audited.append(bug)
    result=dict(selection_checks=checks,proposed_patch_reversal_verified=sorted(audited),
        classification_executed=False,complete=True)
    save('results/p2-selection-validation.json',result)
    print(result)


if __name__=='__main__':main()
