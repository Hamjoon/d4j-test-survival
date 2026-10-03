"""Part 2 delimiter-first extraction; no source repair or normalization."""
import csv
import hashlib
import json
from pathlib import Path
import re

TECHS = ['ZSL', 'FSL', 'CoT', 'ToT', 'GToT']
MARKER = re.compile(r'^\s*(?:\*\*|__|`)?###Test (START|END)#{2,3}(?:\*\*|__|`)?\s*$')


def lexical_masks(text):
    """Mask comments, then comments and literals, preserving offsets."""
    comments, code = list(text), list(text)
    i = 0
    while i < len(text):
        start = i
        if text.startswith('//', i):
            end = text.find('\n', i)
            i = len(text) if end < 0 else end
            comment = True
        elif text.startswith('/*', i):
            end = text.find('*/', i + 2)
            i = len(text) if end < 0 else end + 2
            comment = True
        elif text[i] in '\"\'':
            quote = text[i]
            i += 1
            while i < len(text):
                if text[i] == '\\':
                    i += 2
                elif text[i] == quote:
                    i += 1
                    break
                else:
                    i += 1
            i = min(i, len(text))
            comment = False
        else:
            i += 1
            continue
        for j in range(start, i):
            if text[j] not in '\r\n':
                code[j] = ' '
                if comment:
                    comments[j] = ' '
    return ''.join(comments), ''.join(code)


def extract(text, csr_success=None):
    lines = text.splitlines(keepends=True)
    start = next((i for i, line in enumerate(lines)
                  if (m := MARKER.fullmatch(line)) and m[1] == 'START'), None)
    end = next((i for i in range(start + 1, len(lines))
                if (m := MARKER.fullmatch(lines[i])) and m[1] == 'END'), None) if start is not None else None
    paired = start is not None and end is not None
    selected, source = [], None
    if paired:
        selected, source = lines[start + 1:end], 'markers'
    else:
        blocks, block = [], None
        for line in lines:
            if line.startswith('```'):
                if block is None:
                    block = []
                else:
                    candidate = ''.join(block)
                    if '@Test' in candidate or 'import org.junit' in candidate:
                        blocks.append(block)
                    block = None
            elif block is not None:
                block.append(line)
        if blocks:
            selected = max(blocks, key=lambda b: len(''.join(b)))
            source = 'fence-fallback'
    combined = ''.join(line for line in selected if not line.startswith('```'))
    comments, code = lexical_masks(combined)
    depth, balanced, depths = 0, True, []
    for char in code:
        depths.append(depth)
        if char == '{':
            depth += 1
        elif char == '}':
            depth -= 1
            if depth < 0:
                balanced = False
    balanced = balanced and depth == 0
    public = next((m for m in re.finditer(r'\bpublic\s+class\s+(\w+)', code)
                   if depths[m.start()] == 0), None)
    name = public or re.search(r'\bclass\s+(\w+)', code)
    package = re.search(r'\bpackage\s+([\w.]+)\s*;', code)
    class_name = name[1] if name else None
    pkg = package[1] if package else ''
    junit = '@Test' in combined or 'import org.junit' in combined
    html = bool(re.search(r'<(?:html|body|div|script)\b', comments, re.I))
    # A truncated response can visibly contain code without a complete extractable block.
    detected = bool(combined.strip()) or bool(re.search(r'\bclass\s+\w+', text) and
                                              ('@Test' in text or 'import org.junit' in text))
    return dict(msr_detected=detected, csr_v2=bool(name and junit and balanced and not html),
                combine_v2=combined, class_name=class_name, package=pkg,
                fqcn=(pkg + '.' if pkg else '') + class_name if class_name else None,
                csr_success=csr_success, selection=source, marker_pair=paired,
                braces_balanced=balanced, html_detected=html)


def main():
    with open('results/extraction.csv', newline='') as stream:
        old = {r['file_path']: r for r in csv.DictReader(stream)}
    records = json.loads(Path('results/lang-records.json').read_text())
    rows = []
    for record in sorted(records, key=lambda r: r['bug_id']):
        for tech in TECHS:
            folder = Path('runs/lang') / str(record['bug_id']) / record['class'] / tech
            response = folder / 'response.md'
            raw = response.read_bytes()
            result = extract(raw.decode('utf-8'), old[str(folder / 'raw.java')]['csr_success'] == 'True')
            row = dict(bug_id=record['bug_id'], **{'class': record['class']}, technique=tech,
                       round=1, response=str(response), response_sha256=hashlib.sha256(raw).hexdigest(), **result)
            row['file'] = None
            if result['csr_v2']:
                dest = Path('generated/p2') / f'Lang-{record["bug_id"]}' / 'r1' / tech / (result['class_name'] + '.java')
                dest.parent.mkdir(parents=True, exist_ok=True)
                data = result['combine_v2'].encode('utf-8')
                if dest.exists():
                    assert dest.read_bytes() == data, f'Refuse modifying test: {dest}'
                else:
                    dest.write_bytes(data)
                row.update(file=str(dest), extracted_sha256=hashlib.sha256(data).hexdigest())
            rows.append(row)
    assert len(rows) == 70
    Path('results/p2-extraction-round1.json').write_text(json.dumps(rows, indent=2) + '\n')
    fields = list(dict.fromkeys(key for row in rows for key in row))
    with open('results/p2-extraction-round1.csv', 'w', newline='') as stream:
        writer = csv.DictWriter(stream, fields)
        writer.writeheader()
        writer.writerows(rows)
    lines = ['# Part 2 round-1 extraction', '', '| Technique | Responses | MSR | Bundle CSR | CSR v2 |',
             '|---|---:|---:|---:|---:|']
    for tech in TECHS + ['pooled']:
        group = rows if tech == 'pooled' else [r for r in rows if r['technique'] == tech]
        lines.append(f'| {tech} | {len(group)} | {sum(r["msr_detected"] for r in group)} | '
                     f'{sum(r["csr_success"] for r in group)} | {sum(r["csr_v2"] for r in group)} |')
    lines += ['', 'Source bytes between the selected marker lines (or fallback fences) are retained; '
              'only lines starting with ``` are removed. No stripping, formatting or test repair.', '']
    Path('results/p2-extraction-round1.md').write_text('\n'.join(lines))
    print('\n'.join(lines))
    assert sum(r['csr_v2'] for r in rows) >= 50, 'Stop: CSR v2 below expected 50/70'


if __name__ == '__main__':
    main()
