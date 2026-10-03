"""Read-only audit of the Defects4J patch orientation against both checkouts."""
from pathlib import Path
import re
from generate_rounds import load, save, sha


def parse_patch(text):
    files=[]; current=None; hunk=None
    for line in text.splitlines():
        if line.startswith('--- '):
            current=dict(old_path=line[4:].split('\t')[0].removeprefix('a/'),hunks=[])
            files.append(current)
        elif line.startswith('+++ '):
            current['new_path']=line[4:].split('\t')[0].removeprefix('b/')
        elif line.startswith('@@ '):
            match=re.match(r'@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@',line)
            assert match,line
            hunk=dict(old_start=int(match[1]),old_count=int(match[2] or 1),new_start=int(match[3]),
                      new_count=int(match[4] or 1),lines=[])
            current['hunks'].append(hunk)
        elif hunk is not None and line[:1] in {' ','+','-'}:
            hunk['lines'].append(line)
        elif line.startswith('diff --git'):
            hunk=None
    return files


def changed_lines(file,side):
    result=set()
    marker='-' if side=='old' else '+'
    other='+' if side=='old' else '-'
    for hunk in file['hunks']:
        cursor=hunk[side+'_start']; additions=[]; deletion_positions=[]
        for line in hunk['lines']:
            if line.startswith(marker): additions.append(cursor);cursor+=1
            elif line.startswith(' '): cursor+=1
            elif line.startswith(other): deletion_positions.append(cursor)
        if additions:result.update(additions)
        elif deletion_positions: result.add(max(1,deletion_positions[0]))
    return sorted(result)


def matches(file,checkout,side):
    path=checkout/file[side+'_path']
    if not path.exists():return False
    source=path.read_text().splitlines()
    for hunk in file['hunks']:
        expected=[line[1:] for line in hunk['lines'] if line.startswith(' ') or line.startswith('-' if side=='old' else '+')]
        assert len(expected)==hunk[side+'_count']
        start=hunk[side+'_start']-1
        if source[start:start+len(expected)]!=expected:return False
    return True


def main():
    out=Path('results/p2-patch-audit');out.mkdir(exist_ok=True)
    records=[]
    for r in load('results/lang-records.json'):
        bug=r['bug_id']; source=Path('/defects4j/framework/projects/Lang/patches')/f'{bug}.src.patch'
        copy=out/f'{bug}.src.patch';data=source.read_bytes()
        if copy.exists():assert copy.read_bytes()==data
        else:copy.write_bytes(data)
        files=parse_patch(data.decode())
        row=dict(bug_id=bug,patch=str(copy),sha256=sha(copy),files=[])
        for f in files:
            comparisons={f'{side}_matches_{kind}':matches(f,Path('d4j')/f'Lang-{bug}{kind}',side)
                         for side in ['old','new'] for kind in ['b','f']}
            row['files'].append(dict(old_path=f['old_path'],new_path=f['new_path'],**comparisons,
                old_side_changed_lines=changed_lines(f,'old'),new_side_changed_lines=changed_lines(f,'new')))
        row['orientation']='fixed-to-buggy' if all(f['old_matches_f'] and f['new_matches_b'] for f in row['files']) else 'needs-review'
        records.append(row)
    save(out/'audit.json',dict(records=records,complete=True))
    for r in records:print(f'Lang-{r["bug_id"]}: {r["orientation"]}; {len(r["files"])} modified files')


if __name__=='__main__':main()
