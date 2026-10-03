# Stage 0 version check

**Verdict: mixed/neither**

Comparison converts CRLF to LF, drops non-ASCII characters, strips trailing whitespace per line, and removes trailing blank lines from all three sources. Differing lines are the sum of `max(JSON span, checkout span)` for each non-equal `SequenceMatcher` block (`autojunk=False`).

| Record index (0-based) | Project | Bug | Class | equals_buggy | equals_fixed | buggy_equals_fixed | Diff lines vs buggy | Diff lines vs fixed |
|---:|---|---|---|---|---|---|---:|---:|
| 314 | Lang | 4 | LookupTranslator | True | False | False | 0 | 4 |
| 399 | Csv | 1 | ExtendedBufferedReader | True | False | False | 0 | 1 |
| 235 | Cli | 5 | Util | False | False | False | 1 | 4 |
| 58 | Closure | 4 | NamedType | True | False | False | 0 | 2 |
| 440 | Mockito | 1 | InvocationMatcher | True | False | False | 0 | 12 |

Paths and normalized SHA-256 hashes are recorded in `stage0-version-check.json`.

### Cli-5 Util (closer side: b)

```diff
--- JSON
+++ /work/d4j/Cli-5b/src/java/org/apache/commons/cli/Util.java
@@ -17,7 +17,7 @@
 package org.apache.commons.cli;
 
 /**
- * Contains useful helper methods for classs within this package.
+ * Contains useful helper methods for classes within this package.
  *
  * @author John Keyes (john at integralsource.com)
  */
```
