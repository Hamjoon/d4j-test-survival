# Prompt token check

Tokenizer: cl100k_base (tiktoken 0.14.0).

| Technique | Rendered | Exact | Diff min/max | Max tokens | Above 4,096 |
|---|---:|---:|---|---:|---:|
| ZSL | 477 | 428 | 0/8 | 3700 | 0 |
| FSL | 477 | 428 | 0/8 | 4091 | 0 |
| CoT | 477 | 428 | 0/8 | 3807 | 0 |
| ToT | 477 | 428 | 0/8 | 3762 | 0 |
| GToT | 477 | None | None/None | 3889 | 0 |

Every record has the same token difference across ZSL, FSL, CoT and ToT.

Stop conditions: none.
