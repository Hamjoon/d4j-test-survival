# Part 2 populations at t — Step 5b

LLM methods enter survival only when they passed at t and are the first passing occurrence of their body hash within the record. Hashes use the original source inside method braces with all whitespace removed, including whitespace in comments and string literals. Comments otherwise remain. Method names, annotations and signatures are outside the hash. Ledger order is round, ZSL/FSL/CoT/ToT/GToT, source order. No test source is edited.

| Record / CUT | D_r | D_r_own | L_r raw | Duplicates removed | L_r_unique | Rounds | Unique target reached | ZSL | FSL | CoT | ToT | GToT |
|---|---:|---:|---:|---:|---:|---:|---|---:|---:|---:|---:|---:|
| Lang-4 / LookupTranslator | 136 | 1 | 143 | 2 | 141 | 3 | yes | 26 (18.4%) | 18 (12.8%) | 28 (19.9%) | 40 (28.4%) | 29 (20.6%) |
| Lang-5 / LocaleUtils | 12 | 12 | 130 | 30 | 100 | 1 | yes | 21 (21.0%) | 22 (22.0%) | 20 (20.0%) | 22 (22.0%) | 15 (15.0%) |
| Lang-6 / CharSequenceTranslator | 141 | 0 | 181 | 1 | 180 | 4 | yes | 27 (15.0%) | 24 (13.3%) | 45 (25.0%) | 32 (17.8%) | 52 (28.9%) |
| Lang-11 / RandomStringUtils | 10 | 10 | 104 | 12 | 92 | 1 | yes | 14 (15.2%) | 23 (25.0%) | 18 (19.6%) | 16 (17.4%) | 21 (22.8%) |
| Lang-12 / RandomStringUtils | 8 | 8 | 87 | 6 | 81 | 1 | yes | 0 (0.0%) | 18 (22.2%) | 15 (18.5%) | 29 (35.8%) | 19 (23.5%) |
| Lang-13 / SerializationUtils | 207 | 23 | 283 | 53 | 230 | 6 | yes | 39 (17.0%) | 33 (14.3%) | 47 (20.4%) | 39 (17.0%) | 72 (31.3%) |
| Lang-17 / CharSequenceTranslator | 44 | 0 | 49 | 0 | 49 | 1 | yes | 9 (18.4%) | 8 (16.3%) | 11 (22.4%) | 11 (22.4%) | 10 (20.4%) |
| Lang-19 / NumericEntityUnescaper | 30 | 1 | 53 | 0 | 53 | 1 | yes | 9 (17.0%) | 13 (24.5%) | 11 (20.8%) | 11 (20.8%) | 9 (17.0%) |
| Lang-28 / NumericEntityUnescaper | 19 | 0 | 61 | 0 | 61 | 1 | yes | 11 (18.0%) | 10 (16.4%) | 11 (18.0%) | 11 (18.0%) | 18 (29.5%) |
| Lang-43 / ExtendedMessageFormat | 6 | 6 | 38 | 0 | 38 | 1 | yes | 12 (31.6%) | 0 (0.0%) | 7 (18.4%) | 19 (50.0%) | 0 (0.0%) |
| Lang-54 / LocaleUtils | 11 | 11 | 76 | 14 | 62 | 1 | yes | 23 (37.1%) | 17 (27.4%) | 0 (0.0%) | 0 (0.0%) | 22 (35.5%) |
| Lang-55 / StopWatch | 5 | 5 | 66 | 1 | 65 | 1 | yes | 0 (0.0%) | 16 (24.6%) | 22 (33.8%) | 15 (23.1%) | 12 (18.5%) |
| Lang-57 / LocaleUtils | 0 | 0 | 139 | 35 | 104 | 1 | yes | 28 (26.9%) | 24 (23.1%) | 23 (22.1%) | 15 (14.4%) | 14 (13.5%) |
| Lang-64 / ValuedEnum | 74 | 18 | 99 | 2 | 97 | 3 | yes | 20 (20.6%) | 21 (21.6%) | 16 (16.5%) | 18 (18.6%) | 22 (22.7%) |

Totals: dev 703; dev-own 95; LLM raw 1509, duplicates removed 156, unique 1353.

Technique cells count unique passing methods and their share of the unique population. The full D_r is the generation target. dev-own filters the existing dev population; no additional developer runs or compilation are needed.

Lang-6 and Lang-17 have no matching own test class. Lang-28 has one, but its sole method fails at t. These empty dev-own populations are N/A and excluded from their 2×2 comparison.

Lang-57 developer survival is N/A at every time point: all developer methods are trigger tests; no developer baseline at t. Exclude Lang-57 from both 2×2 comparisons. Its LLM survival uses the unique population.

139 LLM methods passed at the buggy version where every developer method fails. For Lang-57, removing 35 exact duplicates leaves 104 unique passing LLM methods.
