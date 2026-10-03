# Part 2 populations at t

Only methods with status pass at t enter a population. LLM method identities include record, round, technique, test class and method; repeated method names across rounds remain distinct. Counts are not deduplicated across records. Technique cells show method counts and their share of that record’s final LLM population.

| Record / CUT | D_r (target) | D_r_own | L_r | Rounds | Reached full D_r | ZSL | FSL | CoT | ToT | GToT |
|---|---:|---:|---:|---:|---|---:|---:|---:|---:|---:|
| 4 / LookupTranslator | 136 | 1 | 143 | 3 | yes | 26 (18.2%) | 18 (12.6%) | 30 (21.0%) | 40 (28.0%) | 29 (20.3%) |
| 5 / LocaleUtils | 12 | 12 | 130 | 1 | yes | 21 (16.2%) | 27 (20.8%) | 28 (21.5%) | 30 (23.1%) | 24 (18.5%) |
| 6 / CharSequenceTranslator | 141 | 0 (N/A survival) | 181 | 4 | yes | 27 (14.9%) | 24 (13.3%) | 45 (24.9%) | 33 (18.2%) | 52 (28.7%) |
| 11 / RandomStringUtils | 10 | 10 | 104 | 1 | yes | 14 (13.5%) | 26 (25.0%) | 21 (20.2%) | 20 (19.2%) | 23 (22.1%) |
| 12 / RandomStringUtils | 8 | 8 | 87 | 1 | yes | 0 (0.0%) | 18 (20.7%) | 16 (18.4%) | 31 (35.6%) | 22 (25.3%) |
| 13 / SerializationUtils | 207 | 23 | 240 | 5 | yes | 46 (19.2%) | 33 (13.8%) | 45 (18.8%) | 46 (19.2%) | 70 (29.2%) |
| 17 / CharSequenceTranslator | 44 | 0 (N/A survival) | 49 | 1 | yes | 9 (18.4%) | 8 (16.3%) | 11 (22.4%) | 11 (22.4%) | 10 (20.4%) |
| 19 / NumericEntityUnescaper | 30 | 1 | 53 | 1 | yes | 9 (17.0%) | 13 (24.5%) | 11 (20.8%) | 11 (20.8%) | 9 (17.0%) |
| 28 / NumericEntityUnescaper | 19 | 0 (N/A survival) | 61 | 1 | yes | 11 (18.0%) | 10 (16.4%) | 11 (18.0%) | 11 (18.0%) | 18 (29.5%) |
| 43 / ExtendedMessageFormat | 6 | 6 | 38 | 1 | yes | 12 (31.6%) | 0 (0.0%) | 7 (18.4%) | 19 (50.0%) | 0 (0.0%) |
| 54 / LocaleUtils | 11 | 11 | 76 | 1 | yes | 23 (30.3%) | 26 (34.2%) | 0 (0.0%) | 0 (0.0%) | 27 (35.5%) |
| 55 / StopWatch | 5 | 5 | 66 | 1 | yes | 0 (0.0%) | 16 (24.2%) | 22 (33.3%) | 15 (22.7%) | 13 (19.7%) |
| 57 / LocaleUtils | 0 | 0 (N/A survival) | 139 | 1 | yes | 28 (20.1%) | 30 (21.6%) | 29 (20.9%) | 26 (18.7%) | 26 (18.7%) |
| 64 / ValuedEnum | 74 | 18 | 99 | 3 | yes | 21 (21.2%) | 21 (21.2%) | 17 (17.2%) | 18 (18.2%) | 22 (22.2%) |

Totals: full dev 703, dev-own 95, LLM 1466. LLM technique counts: {'ZSL': 247, 'FSL': 270, 'CoT': 293, 'ToT': 311, 'GToT': 345}.

## Developer subset

dev-own matches exactly <CUT package>.<CUT simple name>Test within tests.relevant and filters the existing passing dev methods. The full D_r remains the generation target. No additional developer test compilation or execution was performed.

- Lang-6 and Lang-17 have no matching test class: D_r_own = 0 and dev-own survival is N/A.
- Lang-28 has the matching class, but its only test fails at t: D_r_own = 0 and dev-own survival is N/A. Full-dev reporting remains unchanged.
- Lang-57: D_r = D_r_own = 0; developer survival is N/A at every time point, with note "all developer methods are trigger tests; no developer baseline at t".

The machine-readable population manifest retains population = dev and an own_class boolean. Step 6 must carry own_class into p2-survival-methods.csv. dev-own is a filtered view, not duplicated CSV rows.

## Lang-57 observation and later aggregation

139 LLM methods passed at the buggy version where every developer method fails.

Lang-57 remains in the experiment and its LLM survival is computed normally. Exclude Lang-57 from class-level 2x2 counts and footnote the exclusion. The dev-own 2x2 also omits empty baselines (Lang-6, Lang-17, Lang-28 and Lang-57), reporting them as N/A rather than all-pass.

Every Step 8 table reporting dev must show dev-own beside it: survival by time point, survival by days, failure kinds, class-level 2x2 and counts matched.
