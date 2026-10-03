# Stage 0 Defects4J checks

Started: 2026-10-02T08:57:08.389852+00:00

| Checkout | classes.modified | JSON classes match | Compile | Wall seconds | dir.src.classes | dir.src.tests | cp.test produced |
|---|---|---|---|---:|---|---|---|
| Lang-4b | org.apache.commons.lang3.text.translate.LookupTranslator | True | not run | — | src/main/java | src/test/java | True |
| Lang-4f | org.apache.commons.lang3.text.translate.LookupTranslator | True | not requested | — | src/main/java | src/test/java | True |
| Csv-1b | org.apache.commons.csv.ExtendedBufferedReader | True | not run | — | src/main/java | src/test/java | True |
| Csv-1f | org.apache.commons.csv.ExtendedBufferedReader | True | not requested | — | src/main/java | src/test/java | True |
| Cli-5b | org.apache.commons.cli.Util | True | not run | — | src/java | src/test | True |
| Cli-5f | org.apache.commons.cli.Util | True | not requested | — | src/java | src/test | True |
| Closure-1b | com.google.javascript.jscomp.RemoveUnusedVars | False | not run | — | not run | not run | not run |
| Mockito-1b |  | pending | not run | — | not run | not run | not run |

Raw exports: `stage0-export/<checkout>.txt`. Full command logs: `stage0-logs/`.

## Stop reason

Modified class mismatch for Closure-1b: export=['RemoveUnusedVars'], JSON=[].
