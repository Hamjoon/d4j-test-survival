# Model choice and probe

User-authorized replacement on 2026-10-03: none of the paper's four models is callable through an API as of today, per user availability finding. OpenRouter and Mistral API no longer serve Mistral 7B or Mixtral 8x7B.

Selected model for Step 6 and all later steps: openai/gpt-oss-120b. Parameters: temperature 0.7, max_tokens 4096, one user message, no system message.

The availability finding for all four paper models was supplied by the user. Original OpenRouter model-list output: results/model-list.json; fresh list: results/probe-model-list.json.

Matching Mistral 7B model IDs: .

Record: Lang-28 NumericEntityUnescaper; authors ZSL tokens: 559.

HTTP status: 200; finish_reason: stop; completion tokens: 2266; provider: CoreWeave.
Start marker: True; end marker: True.
Latency: 32.516 seconds; attempt: 1.

First 10 lines:

```text
###Test START##
/*
 * Unit tests for {@link org.apache.commons.lang3.text.translate.NumericEntityUnescaper}.
 *
 * These tests are written for JUnit 4 and aim to cover all logical paths
 * inside the {@code translate} method, including normal operation,
 * hexadecimal handling, error handling and edge‑cases.
 */
package org.apache.commons.lang3.text.translate;

```
