# Lang Part 1 per-record results

Codes: no-code = CSR unsuccessful; no-class = CSR successful without class name; syntax = parse failure; compile = javac failure; run-error = timeout/JVM/runner error; ran = runner completed (passed/total).

| Bug / class | ZSL | FSL | CoT | ToT | GToT |
|---|---|---|---|---|---|
| 4 / LookupTranslator | ran 9/10 | no-code | syntax | no-code | ran 10/10 |
| 5 / LocaleUtils | no-code | no-code | no-code | no-code | no-code |
| 6 / CharSequenceTranslator | no-code | no-code | no-code | no-code | ran 13/16 |
| 11 / RandomStringUtils | run-error | no-code | syntax | ran 20/21 | run-error |
| 12 / RandomStringUtils | no-code | no-code | no-code | no-code | no-code |
| 13 / SerializationUtils | no-code | no-code | no-code | no-code | syntax |
| 17 / CharSequenceTranslator | ran 9/10 | ran 8/12 | no-code | no-code | no-code |
| 19 / NumericEntityUnescaper | ran 9/13 | no-code | no-code | ran 11/12 | no-code |
| 28 / NumericEntityUnescaper | no-code | ran 10/11 | ran 11/12 | ran 11/11 | ran 18/21 |
| 43 / ExtendedMessageFormat | no-code | no-code | no-code | no-code | no-code |
| 54 / LocaleUtils | no-code | ran 26/26 | no-code | no-code | no-code |
| 55 / StopWatch | no-code | ran 16/19 | syntax | no-code | ran 13/17 |
| 57 / LocaleUtils | no-code | no-code | no-code | no-code | no-code |
| 64 / ValuedEnum | syntax | ran 9/10 | syntax | ran 9/9 | no-code |
