"""Offline checks for paid-call retry, preservation and safe resume behavior."""
import json
import os
from pathlib import Path
import tempfile
import requests
from generate_rounds import generate_call, attempt_cost, load


def main():
    previous = Path.cwd()
    with tempfile.TemporaryDirectory(prefix='p2-generation-check-') as temp:
        os.chdir(temp)
        try:
            record = {'bug_id': 4, 'class': 'LookupTranslator'}
            prompt = Path('prompts/rendered/Lang/4/LookupTranslator/ZSL.txt')
            prompt.parent.mkdir(parents=True)
            prompt.write_text('unchanged test prompt\n')
            captured, waits = [], []
            def response(status, value):
                result = requests.Response()
                result.status_code = status
                result._content = json.dumps(value).encode()
                return result
            def post(*args, **kwargs):
                captured.append(kwargs['json'])
                if len(captured) == 1:
                    return response(503, {'error': {'message': 'offline fixture'}})
                return response(200, {'id': 'offline', 'provider': 'fixture', 'usage': {'cost': 0.0123, 'completion_tokens': 20},
                    'choices': [{'finish_reason': 'stop', 'message': {'content': '###Test START##\nimport org.junit.Test; public class T { @Test public void x() {} }\n###Test END##\n'}}]})
            root = generate_call(record, 'ZSL', 2, 'offline-only', post=post, sleeper=waits.append)
            assert len(captured) == 2 and waits == [10]
            assert captured[0] == captured[1] == dict(model='openai/gpt-oss-120b', temperature=0.7, max_tokens=4096,
                messages=[{'role': 'user', 'content': 'unchanged test prompt\n'}])
            assert load(root / 'attempt-1/run.json')['status'] == 503
            assert load(root / 'run.json')['attempt'] == 2
            assert (root / 'raw.java').read_bytes() == (root / 'response.md').read_bytes()
            assert attempt_cost(root) == {'reported_cost_usd': 0.0123, 'cost_missing_successes': 0, 'http_attempts': 2}
            def forbidden(*args, **kwargs):
                raise AssertionError('Completed call must not be repeated')
            assert generate_call(record, 'ZSL', 2, 'offline-only', post=forbidden) == root
            attempts = []
            def failure(*args, **kwargs):
                attempts.append(1)
                return response(429, {'error': 'offline rate-limit fixture'})
            try:
                generate_call(record, 'ZSL', 3, 'offline-only', post=failure, sleeper=lambda _: None)
            except RuntimeError as exc:
                assert 'retry limit' in str(exc)
            else:
                raise AssertionError('Retry cap not enforced')
            assert len(attempts) == 4
            try:
                generate_call(record, 'ZSL', 3, 'offline-only', post=forbidden, sleeper=lambda _: None)
            except RuntimeError as exc:
                assert 'retry limit' in str(exc)
            def uncertain(*args, **kwargs):
                raise requests.ReadTimeout()
            for post_method in [uncertain, forbidden]:
                try:
                    generate_call(record, 'ZSL', 4, 'offline-only', post=post_method, sleeper=lambda _: None)
                except RuntimeError:
                    pass
                else:
                    raise AssertionError('Uncertain transport attempt must block repeat calls')
        finally:
            os.chdir(previous)
    print('Offline retry, retry-cap, exact-request, artifact-preservation and resume checks passed.')


if __name__ == '__main__':
    main()
