"""Record the Stage 0 environment without exposing environment secrets."""

import importlib.metadata
import json
import platform
import subprocess
from datetime import datetime, timezone
from pathlib import Path


def run(*args):
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f"{args}: {result.stdout}")
    return result.stdout.strip()


out = Path('/work/results')
out.mkdir(exist_ok=True)
data = {
    'timestamp_utc': datetime.now(timezone.utc).isoformat(),
    'base': 'ubuntu:22.04',
    'os_release': Path('/etc/os-release').read_text(),
    'architecture': platform.machine(),
    'java': run('java', '-version'),
    'javac': run('javac', '-version'),
    'java_home': '/usr/lib/jvm/java-11',
    'python': platform.python_version(),
    'defects4j_tag': run('git', '-C', '/defects4j', 'describe', '--tags', '--exact-match'),
    'defects4j_commit': run('git', '-C', '/defects4j', 'rev-parse', 'HEAD'),
    'lang_info': run('defects4j', 'info', '-p', 'Lang'),
    'packages': {p: importlib.metadata.version(p) for p in ['tiktoken', 'javalang', 'requests', 'pyyaml']},
    'memory_limit_bytes': Path('/sys/fs/cgroup/memory.max').read_text().strip(),
}
(out / 'stage0-environment.json').write_text(json.dumps(data, indent=2) + '\n')
(out / 'stage0-python-freeze.txt').write_text(run('python3', '-m', 'pip', 'freeze') + '\n')
print(json.dumps(data, indent=2))
