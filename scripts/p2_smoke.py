"""Verify extraction edge cases and actual Part 1 normal/hanging files."""
import json
from pathlib import Path
import subprocess
import tempfile
from extract_v2 import extract
from run_class import run_class


def main():
    source = 'package a;\r\nimport org.junit.Test;\r\n/** <div> { */\r\npublic class T { @Test public void x() { String s="}"; char c=\'{\'; java.util.List<String> v; } }\r\n'
    for wrap in ['', '**', '__', '`']:
        for suffix in ['##', '###']:
            raw = f'{wrap}###Test START{suffix}{wrap}\r\n```java\r\n{source}```\r\n{wrap}###Test END{suffix}{wrap}\r\n'
            result = extract(raw)
            assert result['csr_v2'] and result['combine_v2'] == source and result['fqcn'] == 'a.T'
    assert extract('```java\n' + source + '```\n')['combine_v2'] == source
    assert not extract('###Test START##\ninvalid\n###Test END##\n```java\n' + source + '```')['csr_v2']
    assert not extract('```java\n' + source + '<html>\n```')['csr_v2']
    assert not extract('```java\n' + source + '}\n```')['csr_v2']
    truncated = extract('```java\nimport org.junit.Test; public class T { @Test')
    assert truncated['msr_detected'] and not truncated['csr_v2'] and not truncated['combine_v2']
    subprocess.run(['javac', '-cp', 'tools/junit-4.13.2.jar', '-d', 'tools/runner', 'tools/runner/JsonRunner.java'], check=True)
    results = {}
    with tempfile.TemporaryDirectory(prefix='p2-runner-') as tmp:
        fixtures = {
            'RunnerFixture': '''import org.junit.*;
@FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
public class RunnerFixture {
 @Test public void a_pass() { System.out.println("noise"); }
 @Test public void b_fail() { Assert.fail("expected assertion"); }
 @Test public void c_error() { throw new IllegalStateException("expected error"); }
 @Ignore @Test public void d_ignored() {}
 @Test public void e_timeout() { while(true) {} }
 @Test public void f_afterTimeout() {}
 @Test public void g_assumption() { Assume.assumeTrue(false); }
}''',
            'CrashFixture': '''import org.junit.*;
@FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
public class CrashFixture {
 @Test public void a_pass() {}
 @Test public void b_crash() { System.err.println("crash evidence"); Runtime.getRuntime().halt(7); }
 @Test public void c_unreached() {}
}''',
            'LegacyFixture': '''public class LegacyFixture extends junit.framework.TestCase {
 public void testPass() {}
 public void testFail() { fail("assertion"); }
}'''}
        cp = f'{tmp}:tools/runner:tools/junit-4.13.2.jar:tools/hamcrest-core-1.3.jar'
        for name, content in fixtures.items():
            path = Path(tmp) / (name + '.java')
            path.write_text(content)
            subprocess.run(['javac', '-cp', cp, '-d', tmp, str(path)], check=True)
            summary = run_class(name, cp, Path('results/p2-smoke') / (name + '.jsonl'), 300)
            results[name] = dict(counts=summary['counts'], listed=len(summary['listed_methods']), launches=len(summary['launches']))
        assert results['RunnerFixture']['counts'] == {'pass': 2, 'fail': 1, 'error': 1, 'ignored': 2, 'timeout': 1}
        assert results['RunnerFixture']['launches'] == 2
        assert results['CrashFixture']['counts'] == {'pass': 1, 'not-run': 2}
        assert results['LegacyFixture']['counts'] == {'pass': 1, 'fail': 1}
    for bug, name in [(4, 'LookupTranslator'), (11, 'RandomStringUtils')]:
        folder = Path('generated') / f'Lang-{bug}' / 'ZSL'
        cp = f'{folder}/classes:tools/runner:' + Path(f'results/d4j-export/Lang-{bug}b.cp.test').read_text().strip() + ':tools/junit-4.13.2.jar:tools/hamcrest-core-1.3.jar'
        package = 'org.apache.commons.lang3' + ('.text.translate' if bug == 4 else '')
        summary = run_class(package + '.' + name + 'Test', cp, Path('results/p2-smoke') / f'Lang-{bug}-ZSL.jsonl')
        result = dict(counts=summary['counts'], listed=len(summary['listed_methods']), launches=len(summary['launches']))
        results[f'Lang-{bug}-ZSL'] = result
        print(f'Lang-{bug} ZSL: {result}', flush=True)
        assert len(summary['methods']) == len(summary['listed_methods'])
        assert 'not-run' not in summary['counts']
        if bug == 11:
            assert summary['counts'].get('timeout') == 1, 'Stop: expected exactly one timeout'
        else:
            assert summary['counts'] == {'pass': 9, 'fail': 1}
    Path('results/p2-runner-smoke.json').write_text(json.dumps(dict(extraction_checks='passed', runs=results), indent=2) + '\n')


if __name__ == '__main__':
    main()
