# Lang Part 1 matrix

Model: openai/gpt-oss-120b. One generation per record and technique; no generated-test edits or normalization.

| technique | generated | MSR | CSR | syntax ok | compiles | files that run | test methods run / passed | CUT line cov (mean over compiled files) |
|---|---:|---:|---:|---:|---:|---:|---|---:|
| ZSL | 14 | 14 | 5 | 4 | 4 | 3 | 37 / 31 | 86.12% |
| FSL | 14 | 14 | 5 | 5 | 5 | 5 | 78 / 69 | 97.42% |
| CoT | 14 | 14 | 5 | 1 | 1 | 1 | 12 / 11 | 100.00% |
| ToT | 14 | 14 | 4 | 4 | 4 | 4 | 53 / 51 | 95.98% |
| GToT | 14 | 14 | 6 | 5 | 5 | 4 | 69 / 59 | 91.17% |
| pooled | 70 | 70 | 25 | 19 | 19 | 17 | 249 / 221 | 93.23% |

Coverage is the arithmetic mean of per-file CUT line ratios over compiled files, including files whose tests fail. A missing coverage measurement yields N/A rather than dropping that file. JUnit failures are retained as results; they do not make a file a run-error when the runner produces its summary.

Method totals are observed completed results and include partial timeout runs: the 17 completed files account for 240 methods / 212 passes; the two timeout files add 9 completed methods / 9 passes. The unfinished methods are uncounted, so pooled run counts are lower bounds.
