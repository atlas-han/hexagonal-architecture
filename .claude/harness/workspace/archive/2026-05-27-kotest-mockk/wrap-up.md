# Migration Wrap-up — BuckPal Java → Kotlin (hexagonal, Spring Boot 2.4.3)

**Branch:** `kotlin-migration`
**Generated:** 2026-05-17
**Status:** complete — 10/10 sprints PASS, final build green.

## 1. What shipped

Every Java source under `src/` has been replaced by a Kotlin equivalent
without changing public package paths, class names, or the
`@WebAdapter` / `@PersistenceAdapter` / `@UseCase` semantics the
ArchUnit rules depend on. The Spring Boot app still serves
`POST /accounts/send/{src}/{dst}/{amount}` against H2 with the
`SendMoneySystemTest.sql` fixture, the `application.yml` →
`BuckPalConfigurationProperties` → `MoneyTransferProperties` wiring
continues to bind, and all pre-existing JUnit and ArchUnit assertions
remain green without weakening. Lombok is gone from `build.gradle`,
`Optional` is gone from the codebase, and there are zero `kotlinc`
warnings repo-wide.

## 2. Sprint ledger

| #  | Title                              | Commit    | Status |
|----|------------------------------------|-----------|--------|
| 00 | Build configuration                | `e049e5e` | PASS   |
| 01 | `common/` package                  | `fc0208e` | PASS   |
| 02 | `account/domain/`                  | `159766e` | PASS   |
| 03 | `account/application/port/`        | `7a9be90` | PASS   |
| 04 | `account/application/service/`     | `083a156` | PASS   |
| 05 | `account/adapter/in/web/`          | `82cdaf3` | PASS   |
| 06 | `account/adapter/out/persistence/` | `d013301` | PASS   |
| 07 | Root Spring Boot setup             | `3490335` | PASS   |
| 08 | Test sources                       | `aaf0e4f` | PASS   |
| 09 | Cleanup & verification             | `f283463` | PASS   |

Titles from `spec/product-spec.md` §4. SHAs from
`git log --oneline kotlin-migration`. Status from each
`reviews/sprint-NN-review.md` STATUS line.

## 3. Final verification

```
./gradlew clean build check
```

The original verification was executed by the Sprint 9 Evaluator. The
run log (`.claude/harness/workspace/logs/run-log.md`) was never
populated by the orchestrator (only `.gitkeep` exists), so this section
cites the Evaluator's recorded result rather than re-running the
gradle daemon. From `.claude/harness/workspace/reviews/sprint-09-review.md`
(Behavioral Correctness section, lines 10–32):

```
Re-ran every mandated command with JAVA_HOME=corretto-17.0.13:

- ./gradlew clean → BUILD SUCCESSFUL (exit 0)
- ./gradlew compileKotlin compileTestKotlin → BUILD SUCCESSFUL (exit 0)
- ./gradlew test → BUILD SUCCESSFUL (exit 0)
- ./gradlew check → BUILD SUCCESSFUL (exit 0)
- ./gradlew clean build → BUILD SUCCESSFUL (exit 0)

build/test-results/test/TEST-*.xml per-suite parse:
- BuckPalApplicationTests:          1/0/0
- SendMoneySystemTest:               1/0/0
- DependencyRuleTests:               2/0/0
- AccountTest:                       4/0/0
- ActivityWindowTest:                3/0/0
- SendMoneyServiceTest:              2/0/0
- SendMoneyControllerTest:           1/0/0
- AccountPersistenceAdapterTest:     2/0/0
- Totals: 16 tests / 0 failures / 0 errors / 0 skipped.
```

See `.claude/harness/workspace/reviews/sprint-09-review.md:10-32` for
the full Behavioral Correctness block and lines 124–148 for the
20-item contract checklist (all `[x]`).

## 4. Key learnings (1-line each)

- **Sprint 0** — Gradle 6.8.2 + Lombok 1.18.18 are incompatible with JDK 17 (`Unsupported class file major version 61`); the migration had to start by bumping the wrapper to Gradle 7.6.4 and pinning Lombok to 1.18.30.
- **Sprint 2** — Mockito 3 (no `mockito-inline`) cannot subclass-mock Kotlin's final-by-default classes; `Account` had to be marked `open class` with `open fun`/`open val` on every method/property the tests mocked.
- **Sprints 2 + 4 + 9** — Collapsing `Account.getId(): Optional<AccountId>` touched 3 layers (domain, service, test) living on 3 different sprints; a multi-sprint `Optional` shim with `@get:JvmName("getIdOrNull")` was kept until Sprint 9 finally erased `Optional` from the codebase.
- **Sprint 6** — Kotlin's synthetic-property sugar applies only to *Java* getters, not to Kotlin-declared `fun getX()`; `account.activityWindow.activities` failed to compile and had to be written `account.activityWindow.getActivities()`.
- **Sprint 8** — Under `-Xjsr305=strict` (set in Sprint 0), Mockito's `eq`/`any`/`capture` return `null`, which Kotlin's `Intrinsics.checkNotNullExpressionValue` rejects with NPE *before* the mock interceptor runs; three test-local null-bridge helpers were required to make every `given(...)` / `verify(...)` call site compile and run.

## 5. Next steps for the human

- [ ] Review the diff against `main`:
      `git log --oneline main..kotlin-migration` and
      `git diff main...kotlin-migration`.
- [ ] If satisfied, merge or rebase onto `main`:
      `git checkout main && git merge --no-ff kotlin-migration`
      (or `git checkout kotlin-migration && git rebase main && git push`).
- [ ] Delete the worktree once you are done with it:
      `git worktree remove .claude/worktrees/harness-learnings-wrapup`
      (and any other harness worktrees you no longer need).
- [ ] Decide what to do with deferred work — see
      `.claude/harness/workspace/learnings.md` §5 (operator-overload
      cleanup on `Money.add`/`Money.subtract`, `build.gradle.kts`
      flip, `mockito-inline` adoption to drop `open` on `Account`, a
      runtime test for the `@field:NotNull` null-rejection path,
      extracting Mockito null-bridge helpers, `kotlin-stdlib-jdk8` →
      unified `kotlin-stdlib`, Gradle 8 deprecation warning).

## 6. Artifacts produced

- `spec/product-spec.md` — the plan (10-sprint breakdown + risk register)
- `contracts/sprint-NN-contract.md` × 10 — agreed sprint contracts (all `STATUS: AGREED`; 0 went through `NEEDS_REVISION`)
- `handoffs/sprint-NN-handoff.md` × 10 — Generator self-reports
- `reviews/sprint-NN-review.md` × 10 — Evaluator verdicts (all `STATUS: PASS`; weighted scores 9.05 – 9.55)
- `logs/run-log.md` — *not populated by this run* (only `.gitkeep` present); see `learnings.md` §4 for the recommendation to fix in the next harness run
- `learnings.md` — cross-sprint patterns, surprises, validated Lombok→Kotlin mapping, harness signals, deferred work
- `wrap-up.md` — this file
