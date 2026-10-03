# Defects4J Lang test survival

This repository holds a test-survival experiment on the Defects4J Lang records of the dataset of *Prompt Engineering in LLMs for Automated Unit Test Generation: A Large-Scale Study* (EMSE 31:103, 2026). LLM-generated tests, using the paper's five prompts and `gpt-oss-120b`, and the developers' own tests are frozen at the buggy version of each bug and run unchanged at later versions.

## Weekly report

- [PDF report](docs/d4j-lang-survival-report.pdf)
- [Markdown report](docs/d4j-lang-survival-report.md)

## Where things are

| Path | Contents |
|---|---|
| `docs/` | Handovers and review decisions, written at each stop point of the terminal agent. |
| `results/` | Method-level outcomes in `p2-survival-methods.csv`, aggregation in `p2-survival-summary.md`, and matrices. |
| `runs/` | Every model call, with its request and raw response. |
| `generated/` and `survival/` | Test files compiled and executed per version. |
| `devtests/` | Developer test sources frozen at the buggy version, t. |
| `scripts/` | Generation, evaluation, validation, and aggregation scripts. |
| `tools/` | Runner source; JAR files are not tracked. |
| `docker/` | Experiment environment. |

## The authors' bundle is not distributed

`bundle/` is gitignored. It is the replication bundle provided privately by the paper's authors, containing dataset JSON, prompt scripts, and an extraction reimplementation. To rerun generation or prompt rendering, obtain the bundle from the authors and place it at `bundle/` as described in [the Stage 0 handover](docs/handover-stage0.md) and [the Part 1 handover](docs/handover-part1-a.md). The recorded survival results in `results/` do not depend on having the bundle.

## Reproducing

Use the Docker environment in `docker/`: Ubuntu 22.04, JDK 11, and Defects4J v3.0.1. The API key is read from `docker/.env`, which is gitignored. The step documents and review decisions are in `docs/`.
