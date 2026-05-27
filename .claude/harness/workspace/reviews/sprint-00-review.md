STATUS: PASS

GITLEAKS_VIOLATIONS: SKIPPED
SOLID_VIOLATIONS: NO

# Sprint 00 Review — Analysis + ADR

Branch: `claude/harness-domain-value-objects-5PRde`. Sprint-00 is a read-only
analysis sprint; no production or test source was modified. All six contract
acceptance checks were re-executed independently by the Evaluator (not relying
on the Generator's self-report) and all pass. The pre-sprint hot-fix
(commit `5948495`) is audited and confined to `build.gradle` +
`gradle.properties` — `src/` is untouched.

## Mandatory commands

### 1. Deliverable exists

```
$ test -f .claude/harness/workspace/handoffs/sprint-00-vo-candidates.md; echo $?
0
```
EXIT 0 — PASS.

### 2. Header row contains all required columns

```
$ grep -F "| Candidate | Current type | Decision (ACCEPT/REJECT) | Target VO type name | Implementing sprint | Rationale |" \
    .claude/harness/workspace/handoffs/sprint-00-vo-candidates.md; echo $?
| Candidate | Current type | Decision (ACCEPT/REJECT) | Target VO type name | Implementing sprint | Rationale |
0
```
EXIT 0 — PASS (exactly one matching line).

### 3. All six required keywords present

| Keyword | `grep -c` count | Required | Result |
|---|---|---|---|
| `LocalDateTime` | 2 | >=1 | PASS |
| `withdrawalBalance` | 1 | >=1 | PASS |
| `baselineBalance` | 1 | >=1 | PASS |
| `transferThreshold` | 4 | >=1 | PASS |
| `SendMoneyController` | 2 | >=1 | PASS |
| `BigInteger` | 1 | >=1 | PASS |

All six contract-mandated strings present.

### 4. No production / test source modified

```
$ git diff --quiet -- src/main src/test; echo $?
0
```
EXIT 0 — PASS (working tree clean under `src/`).

### 5. External contracts untouched

```
$ git diff --quiet -- \
    src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt \
    src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt \
    src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt; echo $?
0
```
EXIT 0 — PASS (HTTP boundary and JPA entities byte-identical to baseline).

### 6. `./gradlew test`

```
> Task :compileKotlin UP-TO-DATE
> Task :compileJava NO-SOURCE
> Task :processResources UP-TO-DATE
> Task :classes UP-TO-DATE
> Task :compileTestKotlin UP-TO-DATE
> Task :compileTestJava NO-SOURCE
> Task :processTestResources UP-TO-DATE
> Task :testClasses UP-TO-DATE
> Task :test UP-TO-DATE
> Task :jacocoTestReport UP-TO-DATE

Deprecated Gradle features were used in this build, making it incompatible with Gradle 8.0.

BUILD SUCCESSFUL in 1s
6 actionable tasks: 6 up-to-date
EXIT:0
```
EXIT 0 — PASS, `BUILD SUCCESSFUL`. Tasks were `UP-TO-DATE` because nothing
under `src/` changed since the previous build, which is exactly the contract's
expected outcome for a no-code sprint. To confirm tests really pass (and were
not just skipped), the Evaluator inspected `build/test-results/test/*.xml`;
all 16 test classes report `failures="0" errors="0"`:

| Test class | tests | failures | errors |
|---|---|---|---|
| BuckPalApplicationTests | 1 | 0 | 0 |
| DependencyRuleTests | 2 | 0 | 0 |
| SendMoneySystemTest | 1 | 0 | 0 |
| SendMoneyControllerTest | 1 | 0 | 0 |
| AccountMapperTest | 13 | 0 | 0 |
| AccountPersistenceAdapterTest | 2 | 0 | 0 |
| GetAccountBalanceServiceTest | 1 | 0 | 0 |
| MoneyTransferPropertiesTest | 5 | 0 | 0 |
| SendMoneyServiceTest | 4 | 0 | 0 |
| ThresholdExceededExceptionTest | 2 | 0 | 0 |
| AccountFactoriesTest | 7 | 0 | 0 |
| AccountTest | 4 | 0 | 0 |
| ActivityTest | 13 | 0 | 0 |
| ActivityWindowTest | 3 | 0 | 0 |
| MoneyTest | 29 | 0 | 0 |
| SelfValidatingTest | 2 | 0 | 0 |
| **TOTAL** | **90** | **0** | **0** |

`DependencyRuleTests` (ArchUnit hexagonal check) reports 2/2 passing, so the
hexagonal boundary invariant is intact.

## ADR table verification

The handoff's decision table is read from
`.claude/harness/workspace/handoffs/sprint-00-vo-candidates.md` (lines 10–18).
Each row is compared row-for-row to the spec inventory in
`product-spec.md` (lines 87–94).

| # | Candidate (handoff) | Decision | Target VO | Sprint | Matches spec? |
|---|---|---|---|---|---|
| 1 | Activity-window cutoff (`LocalDateTime`) | ACCEPT | `BaselineDate` | `sprint-01` | YES — spec line 88 says ACCEPT/`BaselineDate`/sprint-01 |
| 2 | Activity occurrence (`Activity.timestamp` etc.) | ACCEPT | `ActivityTimestamp` | `sprint-02` | YES — spec line 89 says ACCEPT/`ActivityTimestamp`/sprint-02 |
| 3 | Mapper baseline aggregates (two `Long`s) | ACCEPT | `BaselineBalanceFigures` | `sprint-03` | YES — spec line 90 says ACCEPT/`BaselineBalanceFigures`/sprint-03 |
| 4 | `Account.baselineBalance: Money` | REJECT | — | — | YES — spec line 91 says REJECT |
| 5 | `BuckPalConfigurationProperties.transferThreshold` | REJECT | — | — | YES — spec line 92 says REJECT |
| 6 | `SendMoneyController` path variables | REJECT | — | — | YES — spec line 93 says REJECT |
| 7 | `Money.amount: BigInteger` narrowing | REJECT / OUT-OF-SCOPE | — | — | YES — spec line 94 says "Out of scope" / risk-register |

Findings:
- All 7 inventory candidates present (1 row each, no flips).
- All 3 ACCEPT rows cite the correct sprint (`sprint-01` / `sprint-02` /
  `sprint-03`) — matches the contract's "Rules for the table" §1 and §2.
- All 4 REJECT rows leave "Implementing sprint" = `—` — matches contract §3.
- "Target VO type name" column uses `—` for every REJECT row — matches §4.
- No 8th `DEFER` row was added; the handoff's Notes section explicitly states
  "No new (8th) candidate was discovered" (handoff line 32–33), which matches
  contract Risk #5.
- Handoff includes the optional "Notes" sub-section (per contract Risk #4)
  flagging that `MoneyTransferProperties.maximumTransferThreshold` is the
  post-conversion (already-Money-wrapped) sibling of
  `BuckPalConfigurationProperties.transferThreshold` — this is correct and
  does not invalidate row 5.
- "Read-verified files" section lists all 20 production files from the
  contract's "Inputs (read-only)" — matches contract.

## Hot-fix audit

Per the orchestrator's notes, commit `5948495` was inserted before sprint-00
to make the build environment runnable on this container (JDK 21 only ships
out of the box; Gradle 7.6.4 requires <= JDK 19).

```
$ git show --stat 5948495
commit 59484958fe3724ec1ad3c945e6509fa33f445557
Author: Claude <noreply@anthropic.com>
Date:   Wed May 27 02:42:27 2026 +0000

    chore(build): pin Gradle launcher JVM to JDK 17 for Gradle 7.6.4 compatibility
    ...
 build.gradle      | 6 ++++++
 gradle.properties | 1 +
 2 files changed, 7 insertions(+)
```

- Exactly **2 files** changed: `build.gradle` (+6) and `gradle.properties`
  (+1). No file under `src/`, no `build.gradle.kts`, no test resources.
- Contract's "Out of scope" §2 says "Editing `build.gradle.kts`". The hot-fix
  touched `build.gradle` (Groovy DSL — this repo does not use `.kts`) and
  `gradle.properties`. The contract's literal text does **not** forbid
  `build.gradle` or `gradle.properties`. Strictly speaking the hot-fix is
  outside the contract's stated out-of-scope list. The spec's product-level
  "Out of scope" (line 194) only names `build.gradle.kts` as well.
- The hot-fix is **independent** of the sprint-00 ADR work and was committed
  separately (not folded into the sprint-00 commit), so the contract's "src/"
  invariants (#4 and #5 above) remain trivially satisfied.
- Conclusion: hot-fix is acceptable. Recording explicitly here so future
  sprints know the JDK pinning is intentional infrastructure, not Generator
  drift.

## ArchUnit / additional checks

- `DependencyRuleTests` (Kotest FunSpec under
  `src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt`) ran inside
  the `./gradlew test` `BUILD SUCCESSFUL` envelope with `tests=2 failures=0
  errors=0`. Hexagonal layer rules still hold (which they trivially must,
  since no source changed).
- `grep -R "import lombok" src/` → no hits (exit code 1), as expected: this
  is a Kotlin codebase, Lombok was removed in prior migrations.
- Sprint-00 changes 0 production lines, so no SOLID surface to audit. Marker:
  `SOLID_VIOLATIONS: NO`.
- `gitleaks` is not installed in this container (`command -v gitleaks` exit
  1). Per evaluator.md §"Security scan" the marker is
  `GITLEAKS_VIOLATIONS: SKIPPED`; this does NOT fail the sprint by itself.

## Bugs found

| # | Severity | Description | Fix |
|---|----------|-------------|-----|
| — | — | none | — |

No defects. No flips. No drift. No new candidate. No secrets. No SOLID
regressions (no production code touched).

## Notes

- The `./gradlew test` UP-TO-DATE behavior is a property of Gradle's
  incremental build cache, not a sign that tests were skipped. The XML
  reports under `build/test-results/test/*.xml` were inspected and confirm
  90 tests passing with zero failures and zero errors across 16 suites.
- The hot-fix's `build.gradle` edit (toolchain pin to JDK 17) is a meta-build
  concern, not a `build.gradle.kts` edit, and lives in a commit separate from
  the sprint-00 workspace commit. Recording for traceability: future sprints
  may rely on this pin, and any unpin would re-trigger the JDK-21
  "Unsupported class file major version 65" failure.
- Working tree currently shows `M .claude/harness/workspace/logs/run-log.md`
  plus three untracked workspace files (`sprint-00-contract.md`,
  `sprint-00-handoff.md`, `sprint-00-vo-candidates.md`). All are workspace
  artefacts, not production source; none affect the sprint-00 invariants.
  The contract's "src/" diff check correctly returned exit 0 even with these
  workspace edits pending.

## Verdict

All six contract acceptance checks pass independently. The ADR table is
faithful to the spec inventory row-for-row, with correct sprint mappings for
the three ACCEPT rows and `—` for the four REJECT rows. The hot-fix commit
is confined to build-toolchain files and does not touch `src/`, so the
sprint-00 "no production code changes" invariant holds. Generator may proceed
to sprint-01.
