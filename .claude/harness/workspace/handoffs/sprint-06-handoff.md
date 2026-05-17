# Sprint 6 Handoff — `account/adapter/out/persistence/`

**Generator:** main session
**Result:** SELF-CHECK GREEN — awaiting Evaluator Phase B verification.

## What changed

Converted 6 production files (1:1, no class restructuring, no API drift):

- `AccountJpaEntity.java` → `AccountJpaEntity.kt` (plain `internal class` JPA entity, `var id: Long? = null`).
- `ActivityJpaEntity.java` → `ActivityJpaEntity.kt` (plain `internal class` JPA entity, 6 nullable `var` columns with defaults).
- `SpringDataAccountRepository.java` → `SpringDataAccountRepository.kt` (`internal interface : JpaRepository<AccountJpaEntity, Long>`).
- `ActivityRepository.java` → `ActivityRepository.kt` (`internal interface : JpaRepository<ActivityJpaEntity, Long>` with 3 `@Query` JPQL methods, JPQL preserved verbatim).
- `AccountMapper.java` → `AccountMapper.kt` (`@Component internal class`, 3 mapping methods; per Evaluator decision: 7 `requireNotNull` calls instead of `!!`; `mapToJpaEntity` uses named args).
- `AccountPersistenceAdapter.java` → `AccountPersistenceAdapter.kt` (`@PersistenceAdapter internal class`, primary-ctor injection of 3 dependencies, `orZero` helper inlined as `?: 0L` Elvis at both call sites).

All 6 Java files deleted from `src/main/java/io/reflectoring/buckpal/account/adapter/out/persistence/`. The directory still exists but is empty of `.java`. Nothing else in the tree was touched.

## Contract checklist — every item PASS

Mechanical greps:

- [x] `find src/main/java/io/reflectoring/buckpal/account/adapter/out -name '*.java'` → **0**
- [x] `find src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence -name '*.kt'` → **6**
- [x] `grep -R "import lombok" src/main/kotlin/io/reflectoring/buckpal/account/adapter/out` → **0**
- [x] `grep -Er "(lateinit|@Autowired)" src/main/kotlin/io/reflectoring/buckpal/account/adapter/out` → **0**
- [x] `grep -R "Optional<" src/main/kotlin/io/reflectoring/buckpal/account/adapter/out` → **0**
- [x] `grep -c '!!' .../AccountMapper.kt` → **0** (per Evaluator decision)
- [x] `grep -c 'requireNotNull' .../AccountMapper.kt` → **7** (1 for `account.id` + 6 for activity fields)
- [x] `grep -R '!!' .../persistence/` → **0 matches** anywhere in scope
- [x] `grep '@PersistenceAdapter' .../AccountPersistenceAdapter.kt` → **1**
- [x] `grep '@Component' .../AccountPersistenceAdapter.kt` → **0** (the `@PersistenceAdapter` marker is the only stereotype — meta-annotated `@Component` via `common/PersistenceAdapter.kt` from Sprint 1)
- [x] `grep '@Entity' .../AccountJpaEntity.kt` → **1**; `.../ActivityJpaEntity.kt` → **1**
- [x] `grep '@Table(name = "account")' .../AccountJpaEntity.kt` → **1**
- [x] `grep '@Table(name = "activity")' .../ActivityJpaEntity.kt` → **1**
- [x] JPQL `select a from ActivityJpaEntity a` → **1** match; `select sum(a.amount) from ActivityJpaEntity a` → **2** matches; all three strings preserved character-for-character (Kotlin uses `+` concatenation matching the Java source line-for-line)
- [x] `grep -c '@Param(' .../ActivityRepository.kt` → **6** (2+2+2 across the 3 query methods)
- [x] `grep -E 'var (ownerAccountId|sourceAccountId|targetAccountId|timestamp|amount): ' .../ActivityJpaEntity.kt | wc -l` → **5**
- [x] `grep -R 'TODO\|FIXME\|XXX' .../persistence/` → **0 matches**

Build / test:

- [x] `./gradlew clean compileKotlin compileJava compileTestKotlin compileTestJava test` → **BUILD SUCCESSFUL** with 16/16 tests passing across 8 suites:
  - `SendMoneyControllerTest` 1/1
  - `AccountPersistenceAdapterTest` 2/2
  - `ActivityWindowTest` 3/3
  - `BuckPalApplicationTests` 1/1
  - `SendMoneyServiceTest` 2/2
  - `AccountTest` 4/4
  - `SendMoneySystemTest` 1/1
  - `DependencyRuleTests` 2/2
- [x] `./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest"` → **2/2 pass** (both `loadsAccount` and `updatesActivities`; this is the critical kotlin-jpa probe — Hibernate successfully instantiates `ActivityJpaEntity` via the plugin-synthesized no-arg ctor)
- [x] `./gradlew test --tests ".AccountPersistenceAdapterTest.loadsAccount"` → **1/1 pass** (isolates kotlin-jpa no-arg ctor + all 3 JPQL queries + mapper's `requireNotNull` block)
- [x] `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"` → **1/1 pass** (full Spring Boot context, H2, `@Sql` fixture, real HTTP)
- [x] `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` → **2/2 pass** (ArchUnit green; package paths preserved)
- [x] `./gradlew check` → **BUILD SUCCESSFUL**
- [x] `./gradlew clean compileKotlin --info | grep -E '^w:|warning:' | wc -l` → **0** (zero kotlinc warnings on the 6 new files)

The `./gradlew test --tests "io.reflectoring.buckpal.archunit.*"` line in the contract is technically unmatched — `archunit/Adapters`, `ApplicationLayer`, `ArchitectureElement`, `HexagonalArchitecture` are all helper classes (still Java; Sprint 8 converts them), not JUnit test classes. The ArchUnit rules they encode are exercised through `DependencyRuleTests` (which is green). Flagging as a contract wording detail rather than a defect.

## Idiomatic Kotlin choices worth flagging

1. **`requireNotNull` block over `!!` for JPA post-load reads.** Per the Evaluator's Phase A decision, the mapper has zero `!!` and exactly 7 `requireNotNull(field) { "<entity> loaded without <field>" }` calls. Each binds a local non-null `val` at the top of the `map { }` lambda before the `Activity(...)` constructor call. Failure messages identify the specific field, which is materially better than a bare `NullPointerException` from `!!`.
2. **Named arguments for all 6 `ActivityJpaEntity` ctor params in `mapToJpaEntity`.** Six same-shape `Long?` parameters are a positional-argument footgun (silent `source` ↔ `target` swap). The named form is audit-friendly and immune to constructor reorderings.
3. **Elvis `?: 0L` replaces the `orZero(...)` Java helper.** `getDepositBalanceUntil` / `getWithdrawalBalanceUntil` return `Long?` (JPQL `sum(...)` is `NULL` on empty result sets); the adapter's two call sites use Elvis inline, eliminating a one-line helper.
4. **`activities.map { ... }.toMutableList()` instead of manual `ArrayList` + `for`-loop.** `ActivityWindow`'s Sprint-2 primary ctor accepts `MutableList<Activity>`, so the `.toMutableList()` call is load-bearing.
5. **Safe-call `activity.id?.value`** replaces the Java `activity.getId() == null ? null : activity.getId().getValue()` ternary in `mapToJpaEntity` — textbook Kotlin null-handling.
6. **`@PersistenceAdapter` as the sole stereotype.** No bare `@Component` added (which would have been redundant since `@PersistenceAdapter` is meta-annotated `@Component` per `common/PersistenceAdapter.kt`). This matches the original Java and keeps ArchUnit's adapter-detection rules intact.
7. **`internal` for all 6 types.** Mirrors the Java package-private visibility; bytecode is `public final` so Spring's component-scan and Spring Data's repository-scan see them without issue.

## Anything the Evaluator should pay extra attention to

1. **kotlin-jpa magic — no-arg ctor synthesis.** Neither entity declares a no-arg constructor; the `org.jetbrains.kotlin.plugin.jpa` v1.6.21 plugin (in `build.gradle`) synthesizes one at compile time. The `loadsAccount` test is the live witness: it runs an `@Sql` fixture against H2, and Hibernate reflectively instantiates rows into `ActivityJpaEntity`. If the plugin were misapplied, that test would fail fast with `InstantiationException`. It passes — confirms the plugin is active and the synthesized ctor works.
2. **kotlin-spring `open` synthesis on `@PersistenceAdapter`.** The plugin opens the class for CGLIB. No `@Transactional` is used in the adapter (so CGLIB is technically not exercised), but the plugin still opens — a non-issue here, listed for completeness.
3. **`@Param` annotations preserved verbatim** (6 total). Strictly speaking Kotlin parameter-name reflection works in modern Spring, but the explicit `@Param` matches the Java source byte-for-byte and is robust to compiler-flag changes (`-parameters` etc.).
4. **`ActivityWindow.getActivities()` is a `fun`, not a property.** As flagged in the Evaluator's contract edit, `account.activityWindow.getActivities()` is the function-call form; Kotlin synthetic-property syntax does not apply to Kotlin-declared `fun getX()` (only to Java getters). The adapter's `updateActivities` for-loop uses the explicit call form.
5. **`AccountId.value: Long` unboxing in the loadAccount path.** `accountId.value` (Kotlin data-class property accessor) replaces `accountId.getValue()`. The `requireNotNull` on `account.id` in `AccountMapper.mapToDomainEntity` is the sole post-load nullability assertion on the `AccountJpaEntity` side — guaranteed non-null after Hibernate populates `@Id`.
6. **`Money.amount.toLong()` vs Java `.longValue()`.** Kotlin's `BigInteger.toLong()` and Java's `BigInteger.longValue()` are the same operation (both truncate silently if `value > Long.MAX_VALUE`). H2 column is `bigint`; fixtures use small values (500/1000); zero risk in practice.
7. **Test reads `savedActivity.getAmount()`.** Java callers (Sprint 8 will convert the test) see `getAmount(): Long` on the Kotlin entity — auto-synthesized JavaBean accessor for `var amount: Long?`. The AssertJ `isEqualTo(1L)` chain unboxes; null would NPE, but Hibernate populates the field after save. Verified green via `updatesActivities`.

## TODOs deferred to later sprints

- **`Account.kt`'s `Optional<AccountId>` shim** (`getId(): Optional<AccountId>`) remains. The new Kotlin mapper consistently uses the nullable `account.id` property (Sprint 2-supplied), so this sprint does not depend on the shim — but does not remove it either. Sprint 9 (or whenever Sprint 8 finishes converting the Java tests that still call `.orElse(...)`) should retire it.
- **`AccountPersistenceAdapterTest.java`** is still Java. Sprint 8 converts it. The Kotlin entity's auto-synthesized `getAmount()` JavaBean accessor keeps the existing assertion working until then.
- **`SendMoneySystemTest.java`** is still Java. Sprint 8 converts it.
- **Lombok dependency** still listed in `build.gradle` because no `.java` file remains? — actually, several `.java` files still exist in test sources (Sprint 8 scope). Sprint 9 removes the Lombok dependency line once all `.java` is gone.

## Self-check summary

All green. No failing tests, no warnings, no contract item unmet. Ready for Evaluator Phase B.

## Commit

Not yet committed. Orchestrator will commit after Evaluator PASS, per harness protocol.
