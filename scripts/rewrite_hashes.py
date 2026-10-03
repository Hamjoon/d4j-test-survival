#!/usr/bin/env python3
"""Update commit references after a history rewrite, using saved git logs."""

import argparse
from datetime import date
from pathlib import Path
import re
import subprocess


def read_commits(path):
    commits = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        commit, author_date, subject = line.split("\t", 2)
        if not re.fullmatch(r"[0-9a-f]{40}", commit):
            raise ValueError(f"Invalid commit hash in {path}: {commit}")
        key = (author_date, subject)
        if key in commits:
            raise ValueError(f"Ambiguous author date and subject in {path}: {key}")
        commits[key] = commit
    if not commits:
        raise ValueError(f"Empty commit list: {path}")
    return commits


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", type=Path, default=Path("/tmp/commits-before.tsv"))
    parser.add_argument("--after", type=Path, default=Path("/tmp/commits-after.tsv"))
    parser.add_argument("--date", type=date.fromisoformat, default=date.today())
    args = parser.parse_args()
    before = read_commits(args.before)
    after = read_commits(args.after)
    if before.keys() != after.keys():
        missing = before.keys() - after.keys()
        added = after.keys() - before.keys()
        raise ValueError(f"Commit lists differ; missing: {sorted(missing)}; added: {sorted(added)}")

    mapping = {old: after[key] for key, old in before.items()}
    prefixes = {old[:7]: old for old in mapping}
    if len(prefixes) != len(mapping):
        raise ValueError("Old commit hashes have ambiguous seven-character prefixes")
    if len({new[:7] for new in mapping.values()}) != len(mapping):
        raise ValueError("New commit hashes have ambiguous seven-character prefixes")
    if any(new[:7] in prefixes and old != new for old, new in mapping.items()):
        raise ValueError("Old and new prefixes overlap; automatic replacement is unsafe")
    pattern = re.compile(r"\b(?:" + "|".join(prefixes) + r")[0-9a-f]*\b")

    root = Path(__file__).resolve().parents[1]
    map_path = root / "docs/commit-hash-map.md"
    tracked = subprocess.check_output(
        ["git", "ls-files", "-z", "--", "docs", "results"], cwd=root
    ).decode("utf-8").split("\0")
    changes = []
    scanned = 0
    for relative in filter(None, tracked):
        path = root / relative
        # Keep the old side of the mapping intact if this script is run again.
        if path == map_path or path.suffix not in {".md", ".json", ".csv", ".txt"}:
            continue
        original = path.read_bytes().decode("utf-8")
        count = 0

        def replace(match):
            nonlocal count
            value = match.group()
            old = prefixes[value[:7]]
            # Do not mistake a longer content digest for a commit abbreviation.
            if not old.startswith(value):
                return value
            replacement = mapping[old][:len(value)]
            count += replacement != value
            return replacement

        updated = pattern.sub(replace, original)
        scanned += 1
        if count:
            changes.append((path, updated, count))

    for path, updated, count in changes:
        path.write_bytes(updated.encode("utf-8"))
        print(f"{path.relative_to(root)}: {count} replacements")

    rows = [
        "# Commit hash map",
        "",
        f"History was rewritten on {args.date.isoformat()} to remove the authors' bundle (`bundle/`).",
        "Commit pairs were matched by unchanged author date and subject.",
        "",
        "| Old commit | New commit |",
        "|---|---|",
        *(f"| `{old}` | `{new}` |" for old, new in mapping.items()),
        "",
    ]
    map_path.write_text("\n".join(rows), encoding="utf-8")
    print(f"Scanned {scanned} files; replaced {sum(item[2] for item in changes)} references in {len(changes)} files.")
    print(f"docs/commit-hash-map.md: {len(mapping)} commit pairs")


if __name__ == "__main__":
    main()
