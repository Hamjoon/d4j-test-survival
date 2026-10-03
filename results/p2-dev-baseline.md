# Part 2 baseline at t

D_r and L_r(1) count passing methods only. Each record has its own population; the same developer method appearing in multiple records is counted within each record. Failures and exceptions are excluded from the passing population. Ignored tests are listed separately.

| Record / CUT | Dev classes | Missing | Compiled | Dev methods listed | D_r | Fail/error at t | Trigger fail/error | Timeouts | L_r(1) | Target reached |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 4 / LookupTranslator | 4 | 0 | 4 | 137 | 136 | 1 | 1/1 | 0 | 53 | no |
| 5 / LocaleUtils | 1 | 0 | 1 | 13 | 12 | 1 | 1/1 | 0 | 130 | yes |
| 6 / CharSequenceTranslator | 9 | 0 | 9 | 142 | 141 | 1 | 1/1 | 0 | 57 | no |
| 11 / RandomStringUtils | 1 | 0 | 1 | 11 | 10 | 1 | 1/1 | 0 | 104 | yes |
| 12 / RandomStringUtils | 1 | 0 | 1 | 10 | 8 | 2 | 2/2 | 0 | 87 | yes |
| 13 / SerializationUtils | 12 | 0 | 12 | 208 | 207 | 1 | 1/1 | 0 | 43 | no |
| 17 / CharSequenceTranslator | 8 | 0 | 8 | 45 | 44 | 1 | 1/1 | 0 | 49 | yes |
| 19 / NumericEntityUnescaper | 3 | 0 | 3 | 32 | 30 | 2 | 2/2 | 0 | 53 | yes |
| 28 / NumericEntityUnescaper | 2 | 0 | 2 | 20 | 19 | 1 | 1/1 | 0 | 61 | yes |
| 43 / ExtendedMessageFormat | 1 | 0 | 1 | 7 | 6 | 1 | 1/1 | 0 | 38 | yes |
| 54 / LocaleUtils | 1 | 0 | 1 | 12 | 11 | 1 | 1/1 | 0 | 76 | yes |
| 55 / StopWatch | 1 | 0 | 1 | 6 | 5 | 1 | 1/1 | 0 | 66 | yes |
| 57 / LocaleUtils | 1 | 0 | 1 | 11 | 0 | 11 | 11/11 | 0 | 139 | yes |
| 64 / ValuedEnum | 4 | 0 | 4 | 75 | 74 | 1 | 1/1 | 0 | 30 | no |

## Round-1 LLM details

| Record | Structured files | Compiled files | Listed methods | Pass | Fail | Error | Ignored | Timeout | Not-run |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 4 | 5 | 5 | 57 | 53 | 4 | 0 | 0 | 0 | 0 |
| 5 | 5 | 5 | 139 | 130 | 9 | 0 | 0 | 0 | 0 |
| 6 | 5 | 4 | 62 | 57 | 3 | 2 | 0 | 0 | 0 |
| 11 | 5 | 5 | 108 | 104 | 1 | 1 | 0 | 2 | 0 |
| 12 | 4 | 4 | 96 | 87 | 0 | 8 | 0 | 1 | 0 |
| 13 | 5 | 3 | 46 | 43 | 1 | 2 | 0 | 0 | 0 |
| 17 | 5 | 5 | 65 | 49 | 16 | 0 | 0 | 0 | 0 |
| 19 | 5 | 5 | 61 | 53 | 7 | 1 | 0 | 0 | 0 |
| 28 | 5 | 5 | 66 | 61 | 4 | 1 | 0 | 0 | 0 |
| 43 | 5 | 3 | 53 | 38 | 8 | 7 | 0 | 0 | 0 |
| 54 | 4 | 3 | 80 | 76 | 4 | 0 | 0 | 0 | 0 |
| 55 | 5 | 4 | 75 | 66 | 9 | 0 | 0 | 0 | 0 |
| 57 | 5 | 5 | 145 | 139 | 3 | 3 | 0 | 0 | 0 |
| 64 | 5 | 3 | 32 | 30 | 2 | 0 | 0 | 0 | 0 |

## Trigger methods

| Bug | Trigger method | Status at t |
|---|---|---|
| 4 | `org.apache.commons.lang3.text.translate.LookupTranslatorTest::testLang882` | fail |
| 5 | `org.apache.commons.lang3.LocaleUtilsTest::testLang865` | error |
| 6 | `org.apache.commons.lang3.StringUtilsTest::testEscapeSurrogatePairs` | error |
| 11 | `org.apache.commons.lang3.RandomStringUtilsTest::testLANG807` | fail |
| 12 | `org.apache.commons.lang3.RandomStringUtilsTest::testExceptions` | error |
| 12 | `org.apache.commons.lang3.RandomStringUtilsTest::testLANG805` | error |
| 13 | `org.apache.commons.lang3.SerializationUtilsTest::testPrimitiveTypeClassSerialization` | error |
| 17 | `org.apache.commons.lang3.StringEscapeUtilsTest::testLang720` | fail |
| 19 | `org.apache.commons.lang3.text.translate.NumericEntityUnescaperTest::testUnfinishedEntity` | error |
| 19 | `org.apache.commons.lang3.text.translate.NumericEntityUnescaperTest::testOutOfBounds` | error |
| 28 | `org.apache.commons.lang3.text.translate.NumericEntityUnescaperTest::testSupplementaryUnescaping` | fail |
| 43 | `org.apache.commons.lang.text.ExtendedMessageFormatTest::testEscapedQuote_LANG_477` | error |
| 54 | `org.apache.commons.lang.LocaleUtilsTest::testLang328` | error |
| 55 | `org.apache.commons.lang.time.StopWatchTest::testLang315` | fail |
| 57 | `org.apache.commons.lang.LocaleUtilsTest::testAvailableLocaleSet` | error |
| 57 | `org.apache.commons.lang.LocaleUtilsTest::testIsAvailableLocale` | error |
| 57 | `org.apache.commons.lang.LocaleUtilsTest::testAvailableLocaleList` | error |
| 57 | `org.apache.commons.lang.LocaleUtilsTest::testCountriesByLanguage` | error |
| 57 | `org.apache.commons.lang.LocaleUtilsTest::testLocaleLookupList_LocaleLocale` | error |
| 57 | `org.apache.commons.lang.LocaleUtilsTest::testLanguagesByCountry` | error |
| 57 | `org.apache.commons.lang.LocaleUtilsTest::testToLocale_1Part` | error |
| 57 | `org.apache.commons.lang.LocaleUtilsTest::testToLocale_2Part` | error |
| 57 | `org.apache.commons.lang.LocaleUtilsTest::testToLocale_3Part` | error |
| 57 | `org.apache.commons.lang.LocaleUtilsTest::testLocaleLookupList_Locale` | error |
| 57 | `org.apache.commons.lang.LocaleUtilsTest::testConstructor` | error |
| 64 | `org.apache.commons.lang.enums.ValuedEnumTest::testCompareTo_otherEnumType` | fail |

## Totals

Developer: {'pass': 703, 'fail': 6, 'error': 20}.

LLM round 1: {'fail': 71, 'pass': 986, 'error': 25, 'timeout': 3}.

Classpath order: copied/generated classes, tools/runner, the checkout cp.test, pinned JUnit/Hamcrest jars. The older project JUnit included by cp.test therefore retains precedence as prescribed.
