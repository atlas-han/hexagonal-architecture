# Workspace

This directory is the **only** communication channel between the three
harness agents. All hand-offs are markdown files; no out-of-band state.

| Subdirectory | Writer | Reader(s) | Lifecycle |
|--------------|--------|-----------|-----------|
| `spec/`      | Planner | Generator, Evaluator | written once per migration |
| `contracts/` | Generator (initial), Evaluator (review comments) | both | one file per sprint, agreed before code |
| `handoffs/`  | Generator | Evaluator | one file per sprint, after self-check passes |
| `reviews/`   | Evaluator | Generator | one file per sprint, after handoff verified |
| `logs/`      | any agent | humans / future agents | append-only narrative trace |

Naming convention: `sprint-<NN>-<kind>.md` where `NN` is zero-padded
(`sprint-00-contract.md`, `sprint-00-handoff.md`, `sprint-00-review.md`).

Files here are **part of the project history**. Commit them alongside the
code changes from their sprint so future readers can reconstruct what was
agreed, what was built, and how it was graded.
