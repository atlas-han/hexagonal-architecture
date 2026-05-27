STATUS: AGREED

# Sprint 04 Contract — Final verification + VO convention notes

## Sprint goal

Prove the system is fully green end-to-end with the three new VOs
(`BaselineDate`, `ActivityTimestamp`, `BaselineBalanceFigures`) in place,
document the boundary-discipline convention so future contributors keep it,
and confirm nothing was left half-converted.

## Deliverable

1. **Verification only.** No new VOs are introduced this sprint. The
   Generator MAY make micro-edits to call sites it missed (e.g. an
   overlooked test fixture). Each micro-edit must be justified in the
   handoff.
2. New file
   `.claude/harness/workspace/handoffs/sprint-04-vo-convention.md`
   containing:
   - **Where VOs live**: a one-paragraph note that all new VOs live in
     `io.reflectoring.buckpal.account.domain` and are imported by
     application and adapter layers (allowed direction).
   - **INTENTIONAL primitive leaks** — the boundaries where raw
     primitives are kept on purpose:
       - HTTP path variables in `SendMoneyController` (the URL contract).
       - Spring `@ConfigurationProperties.transferThreshold: Long` (the
         YAML/property binder).
       - JPA `ActivityJpaEntity.timestamp: LocalDateTime?` and other JPA
         column types (the schema).
       - `ActivityRepository` HQL `:since`/`:until` parameter types
         (HQL doesn't see VOs).
   - **ADR table from sprint-00** copied verbatim (the 7-row decision
     table) — for posterity in case sprint-00 ever gets re-archived.
   - **Quick reference**:
       - `BaselineDate` — activity-window cutoff. Wraps `LocalDateTime`.
         `companion object { fun now(): BaselineDate }`.
       - `ActivityTimestamp` — when an Activity occurred. Wraps
         `LocalDateTime`. `companion object { fun now() }`.
       - `BaselineBalanceFigures` — deposit/withdrawal pair fed into the
         mapper. `data class` (2 fields, not value class). Helper
         `toBaselineBalance(): Money = deposit - withdrawal`.
3. No production source changes are *required*; if any are made, they
   must be tiny call-site fix-ups (no semantic change), and the handoff
   must enumerate them with rationale.

## Inputs

- `.claude/harness/workspace/spec/product-spec.md` — sprint-04 section.
- `.claude/harness/workspace/handoffs/sprint-00-vo-candidates.md` — ADR.
- `.claude/harness/workspace/handoffs/sprint-{01,02,03}-handoff.md`.

## Acceptance checks (Evaluator runs these directly)

1. Full clean build + check is green:
   ```
   ./gradlew clean build check
   ```
   Expected: BUILD SUCCESSFUL, exit 0. All tests pass. ArchUnit
   `DependencyRuleTests` passes.

2. ArchUnit hexagonal check passes in isolation:
   ```
   ./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"
   ```
   Expected: BUILD SUCCESSFUL, exit 0.

3. Spring context still boots:
   ```
   ./gradlew test --tests "io.reflectoring.buckpal.BuckPalApplicationTests"
   ```
   Expected: BUILD SUCCESSFUL, exit 0.

4. End-to-end HTTP transfer still works:
   ```
   ./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"
   ```
   Expected: BUILD SUCCESSFUL, exit 0.

5. All VO constructions live inside the bounded context. Use `grep -rn`
   (since `rg` may not be available) — equivalent semantics:
   ```
   grep -rEn 'BaselineDate\(|ActivityTimestamp\(|BaselineBalanceFigures\(' \
     src/main/kotlin
   ```
   Expected: every matching line is under
   `src/main/kotlin/io/reflectoring/buckpal/account/(domain|application|adapter)/`.
   No hit in `common/`, no hit in `BuckPalConfiguration*`, no hit at the
   package root.

6. No leftover raw `LocalDateTime` parameter in the application/port
   surface:
   ```
   grep -rn 'LocalDateTime' \
     src/main/kotlin/io/reflectoring/buckpal/account/application/port
   ```
   Expected: no matches (exit 1).

7. The convention handoff exists:
   ```
   test -f .claude/harness/workspace/handoffs/sprint-04-vo-convention.md
   ```
   Expected: exit 0.

8. The convention handoff covers all three VOs and the three INTENTIONAL
   primitive leak boundaries:
   ```
   grep -q 'BaselineDate' .claude/harness/workspace/handoffs/sprint-04-vo-convention.md \
     && grep -q 'ActivityTimestamp' .claude/harness/workspace/handoffs/sprint-04-vo-convention.md \
     && grep -q 'BaselineBalanceFigures' .claude/harness/workspace/handoffs/sprint-04-vo-convention.md \
     && grep -q 'SendMoneyController' .claude/harness/workspace/handoffs/sprint-04-vo-convention.md \
     && grep -q 'transferThreshold' .claude/harness/workspace/handoffs/sprint-04-vo-convention.md \
     && grep -q 'ActivityJpaEntity' .claude/harness/workspace/handoffs/sprint-04-vo-convention.md
   ```
   Expected: exit 0.

9. JPA / HQL / SQL / HTTP boundary unchanged across the whole branch since
   the pre-sprint-00 baseline. Baseline commit is the immediate parent of
   the sprint-00 commit (`b3ddc61^` = `5948495`, the JDK17 hot-fix; that
   commit is the cleanest pre-VO baseline):
   ```
   git diff 5948495..HEAD -- \
     src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt \
     src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt \
     src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt \
     src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt \
     src/test/resources
   ```
   Expected: no output (empty diff).

10. No Lombok regressions anywhere:
    ```
    grep -R "import lombok" src/
    ```
    Expected: no matches (exit 1).

11. The sprint-00 ADR file is referenced/copied into the convention
    handoff. Specifically the convention file must mention all 7 candidate
    rows by keyword (so future readers don't have to follow the link):
    ```
    for kw in LocalDateTime withdrawalBalance baselineBalance transferThreshold SendMoneyController BigInteger; do
      grep -q "$kw" .claude/harness/workspace/handoffs/sprint-04-vo-convention.md || echo "MISSING: $kw"
    done
    ```
    Expected: no MISSING line output (each grep exits 0).

## Out of scope

- Introducing additional VOs not on the sprint-00 ACCEPT list.
- Reworking the `Money` API.
- Editing `build.gradle`, `gradle.properties`, wrapper.
- Renaming any existing class or package.
- Editing `README.md` or any production documentation file.

## Risks

- **Convention drift.** If the Generator misses a VO or a boundary in the
  convention note, future readers might propose redundant or
  layer-violating VOs. Mitigated by check 8 + check 11.
- **Hidden raw-`LocalDateTime` parameters.** Check 6 grep is narrow
  (`account/application/port` only) — leaks in `service/` are accepted
  (they live below the port surface). This is intentional: the port
  surface is what other layers see.
- **Forgotten convenience overload.** Sprint-02 left a
  `ActivityTestData.withTimestamp(LocalDateTime)` convenience overload.
  This is in `src/test/kotlin/`, not `src/main/kotlin/`, so check 5's
  `src/main/kotlin` scope correctly ignores it.
- **rg vs grep.** Spec uses `rg`; this contract uses `grep -rn`/`grep -rEn`
  with equivalent semantics, because `rg` is not guaranteed to be
  installed in the harness sandbox.

## Revision history

- v1 — Initial draft. Adds mechanical checks for VO-construction
  containment (check 5), application/port LocalDateTime-leak guard (check
  6), JPA/HTTP boundary diff against the pre-sprint-00 baseline (check
  9), and ADR keyword coverage in the convention handoff (check 11).
