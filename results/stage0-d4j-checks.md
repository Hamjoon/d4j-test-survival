# Stage 0 Defects4J checks

Started: 2026-10-02T09:45:59.167289+00:00

Per the 2026-10-02 addendum, Closure-1b was checked out and then removed along with its active export file; the initial evidence is archived under `archive/stage0-initial-stop/`. Closure-4b/4f replaces it, and Mockito-1f is included. All ten comparisons are required.

| Checkout | classes.modified | JSON classes match | Compile | Wall seconds | dir.src.classes | dir.src.tests | cp.test produced |
|---|---|---|---|---:|---|---|---|
| Lang-4b | org.apache.commons.lang3.text.translate.LookupTranslator | True | success | 2.233 | src/main/java | src/test/java | True |
| Lang-4f | org.apache.commons.lang3.text.translate.LookupTranslator | True | not requested | — | src/main/java | src/test/java | True |
| Csv-1b | org.apache.commons.csv.ExtendedBufferedReader | True | success | 0.834 | src/main/java | src/test/java | True |
| Csv-1f | org.apache.commons.csv.ExtendedBufferedReader | True | not requested | — | src/main/java | src/test/java | True |
| Cli-5b | org.apache.commons.cli.Util | True | success | 0.856 | src/java | src/test | True |
| Cli-5f | org.apache.commons.cli.Util | True | not requested | — | src/java | src/test | True |
| Closure-4b | com.google.javascript.rhino.jstype.NamedType | True | success | 8.469 | src | test | True |
| Closure-4f | com.google.javascript.rhino.jstype.NamedType | True | not requested | — | src | test | True |
| Mockito-1b | org.mockito.internal.invocation.InvocationMatcher | True | success | 25.785 | src | test | True |
| Mockito-1f | org.mockito.internal.invocation.InvocationMatcher | True | not requested | — | src | test | True |

Raw exports: `stage0-export/<checkout>.txt`. Full command logs: `stage0-logs/`.

## Lang-4b developer tests

```json
{
  "returncode": 0,
  "wall_seconds": 28.527,
  "stdout": "Failing tests: 1\n  - org.apache.commons.lang3.text.translate.LookupTranslatorTest::testLang882\n",
  "expected_triggering_tests": [
    "org.apache.commons.lang3.text.translate.LookupTranslatorTest::testLang882"
  ],
  "actual_failing_tests": [
    "org.apache.commons.lang3.text.translate.LookupTranslatorTest::testLang882"
  ],
  "failing_count": 1,
  "matches_known_triggers": true
}
```
