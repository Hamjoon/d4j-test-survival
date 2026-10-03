"""Extract exact Mistral prompt strings using AST, without executing author scripts."""
import ast
import hashlib
import json
from pathlib import Path

SOURCES = [('zero_shot_learning.py','prompt_zeroshot','ZSL'),('few_shot_learning.py','fewshot_example','fewshot_example'),('few_shot_learning.py','prompt_fewshot','FSL'),('chain_of_thought.py','prompt_cot','CoT'),('tree_of_thought.py','prompt_tot','ToT'),('tree_of_thought_2.py','prompt_tot_2','GToT')]

def extract(source, variable):
    nodes=[n.value for n in ast.walk(ast.parse(source)) if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id==variable for t in n.targets)]
    assert len(nodes)==1, (variable,len(nodes))
    node=nodes[0]
    if isinstance(node,ast.Constant):
        assert isinstance(node.value,str)
        return node.value
    assert isinstance(node,ast.JoinedStr)
    pieces=[]
    for part in node.values:
        if isinstance(part,ast.Constant):
            assert isinstance(part.value,str)
            pieces.append(part.value)
        else:
            assert isinstance(part,ast.FormattedValue) and part.conversion==-1 and part.format_spec is None
            expression=ast.get_source_segment(source,part.value)
            assert expression in {'class_name','source_code','fewshot_example'}
            pieces.append('{'+expression+'}')
    return ''.join(pieces)

def main():
    target=Path('prompts/templates'); target.mkdir(parents=True,exist_ok=True)
    provenance={}
    for filename,variable,tech in SOURCES:
        path=Path('bundle/historical_scripts/mistral')/filename
        source=path.read_text(encoding='utf-8')
        template=extract(source,variable)
        (target/(tech+'.txt')).write_bytes(template.encode('utf-8'))
        provenance[tech]={'source':str(path),'variable':variable,'source_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'template_sha256':hashlib.sha256(template.encode('utf-8')).hexdigest()}
    zsl=(target/'ZSL.txt').read_text(); fsl=(target/'FSL.txt').read_text(); example=(target/'fewshot_example.txt').read_text()
    assert '{fewshot_example}' not in zsl
    assert fsl.count('{fewshot_example}')==1
    assert fsl.startswith('As a professional software tester who writes Java test methods, consider the following examples:')
    assert example.startswith('\n//Example Java Class:')
    Path('results/template-check.json').write_text(json.dumps({'checks_passed':True,'templates':provenance},indent=2)+'\n')
    print('Six AST template extractions and all template checks passed.')

if __name__=='__main__': main()
