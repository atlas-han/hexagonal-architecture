STATUS: AGREED
// EVALUATOR: contract reviewed 2026-05-17. Status flipped to AGREED after the
// inline edits below are folded in by the Generator at implementation time.
// Generator: treat every `// EVALUATOR:` block in this file as binding —
// they are part of the agreed contract, not suggestions.

# Sprint 6 Contract — `account/adapter/out/persistence/`

**Status:** DRAFT (awaiting Evaluator review)
**Generator:** main session
**Sprint goal (from spec):** Convert all 6 persistence files (highest-risk
sprint due to JPA + Kotlin interactions).

## Files in scope

Production (6 .java → 6 .kt):

| Java file | Kotlin equivalent |
|-----------|-------------------|
| `account/adapter/out/persistence/AccountJpaEntity.java`         | `account/adapter/out/persistence/AccountJpaEntity.kt` |
| `account/adapter/out/persistence/ActivityJpaEntity.java`        | `account/adapter/out/persistence/ActivityJpaEntity.kt` |
| `account/adapter/out/persistence/AccountMapper.java`            | `account/adapter/out/persistence/AccountMapper.kt` |
| `account/adapter/out/persistence/SpringDataAccountRepository.java` | `account/adapter/out/persistence/SpringDataAccountRepository.kt` |
| `account/adapter/out/persistence/ActivityRepository.java`       | `account/adapter/out/persistence/ActivityRepository.kt` |
| `account/adapter/out/persistence/AccountPersistenceAdapter.java`| `account/adapter/out/persistence/AccountPersistenceAdapter.kt` |

Nothing else is touched. In particular:
- `Account.kt`'s `getId(): Optional<AccountId>` shim is **NOT** removed —
  `SendMoneyServiceTest.java` and `AccountPersistenceAdapterTest.java` are
  still Java (Sprint 8) and rely on Sprint 2 / Sprint 4 decisions. The
  shim stays. The new Kotlin mapper / adapter MUST use the nullable
  `account.id` property (not `account.getId()`/`Optional`) to avoid
  reintroducing `Optional<>` in this sprint's scope.
- JPQL `@Query` strings reference the simple class name `ActivityJpaEntity`
  — the Kotlin class MUST be named exactly that (no rename, no shortening).
- The H2 schema is auto-generated from the entity fields (`hbm2ddl.auto =
  create-drop` for the test profile). Column names are derived from
  Hibernate's default camelCase → snake_case naming strategy
  (`ownerAccountId` → `owner_account_id` etc.) — verified via the
  SQL fixtures (`AccountPersistenceAdapterTest.sql`,
  `SendMoneySystemTest.sql`) which use `owner_account_id`,
  `source_account_id`, `target_account_id`. Kotlin property names MUST
  remain `ownerAccountId`, `sourceAccountId`, `targetAccountId`,
  `timestamp`, `amount`, `id` — verbatim, no rename.

## Conversion targets

### 1. `AccountJpaEntity.kt`

- **Top-level type kind:** plain `class` (NOT `data class`); `internal` (Java was package-private).
- **JPA annotations preserved:** `@Entity`, `@Table(name = "account")`.
- **kotlin-jpa plugin** synthesizes the no-arg ctor (build.gradle already has `org.jetbrains.kotlin.plugin.jpa` v1.6.21).
- **Shape:**

```kotlin
@Entity
@Table(name = "account")
internal class AccountJpaEntity(
    @Id
    @GeneratedValue
    var id: Long? = null,
)
```

- `var` (not `val`) — JPA requires mutable properties for lazy loading and Hibernate proxy generation.
- `Long?` with default `null` — matches Java `Long id` field semantics under `@NoArgsConstructor`.
- No `@Data` Lombok replacement methods (`equals`/`hashCode`/`toString`) are needed: Java callers (none post-conversion) and the test code only use the generated getter/setter shape. Kotlin properties expose `getId()`/`setId(...)` to Java via JavaBean naming, which is what the test asserts (`savedActivity.getAmount()`).

### 2. `ActivityJpaEntity.kt`

- **Top-level type kind:** plain `class` (NOT `data class`); `internal`.
- **JPA annotations preserved:** `@Entity`, `@Table(name = "activity")`.
- **Class simple name MUST remain `ActivityJpaEntity`** — referenced in JPQL strings.
- **Shape:**

```kotlin
@Entity
@Table(name = "activity")
internal class ActivityJpaEntity(
    @Id
    @GeneratedValue
    var id: Long? = null,

    @Column
    var timestamp: LocalDateTime? = null,

    @Column
    var ownerAccountId: Long? = null,

    @Column
    var sourceAccountId: Long? = null,

    @Column
    var targetAccountId: Long? = null,

    @Column
    var amount: Long? = null,
)
```

- All columns nullable with default `null` to retain `@NoArgsConstructor` semantics under kotlin-jpa.
- `@Column` annotation kept on each property (preserves the Java placement on the field).
- `import javax.persistence.*` (Spring Boot 2.4.3 still uses the `javax.persistence` namespace, NOT `jakarta`).

### 3. `AccountMapper.kt`

- **Top-level type kind:** plain `class` (stateless); `internal`; `@Component`.
- **Method signatures preserved 1:1** — all three methods package-private in Java → `internal` in Kotlin.
- **Shape:**

```kotlin
@Component
internal class AccountMapper {

    fun mapToDomainEntity(
        account: AccountJpaEntity,
        activities: List<ActivityJpaEntity>,
        withdrawalBalance: Long,
        depositBalance: Long,
    ): Account {
        val baselineBalance = Money.subtract(
            Money.of(depositBalance),
            Money.of(withdrawalBalance),
        )

        // EVALUATOR: same decision as below — use requireNotNull rather than
        // `account.id!!` so the failure message identifies which entity field
        // was unexpectedly null. Bind to a local `val` so the call site reads
        // cleanly.
        val accountId = requireNotNull(account.id) {
            "AccountJpaEntity loaded without id"
        }
        return Account.withId(
            Account.AccountId(accountId),
            baselineBalance,
            mapToActivityWindow(activities),
        )
    }

    fun mapToActivityWindow(activities: List<ActivityJpaEntity>): ActivityWindow {
        val mappedActivities = activities.map { activity ->
            // EVALUATOR: decision (!! vs requireNotNull) — use requireNotNull
            // at the TOP of the lambda for all six properties, binding to
            // local non-null `val`s. Rationale: (1) the rubric (line 99-101)
            // accepts commented `!!` but Idiomatic Kotlin scores higher when
            // null-pointer triggers carry a message; (2) six inline `!!` on
            // one constructor call is noisy and the per-field comment ends
            // up identical for all six ("guaranteed non-null after Hibernate
            // load") — a single block of `requireNotNull` with field-named
            // messages is shorter AND more informative on failure.
            val id = requireNotNull(activity.id) { "ActivityJpaEntity loaded without id" }
            val ownerAccountId = requireNotNull(activity.ownerAccountId) {
                "ActivityJpaEntity loaded without ownerAccountId"
            }
            val sourceAccountId = requireNotNull(activity.sourceAccountId) {
                "ActivityJpaEntity loaded without sourceAccountId"
            }
            val targetAccountId = requireNotNull(activity.targetAccountId) {
                "ActivityJpaEntity loaded without targetAccountId"
            }
            val timestamp = requireNotNull(activity.timestamp) {
                "ActivityJpaEntity loaded without timestamp"
            }
            val amount = requireNotNull(activity.amount) {
                "ActivityJpaEntity loaded without amount"
            }
            Activity(
                Activity.ActivityId(id),
                Account.AccountId(ownerAccountId),
                Account.AccountId(sourceAccountId),
                Account.AccountId(targetAccountId),
                timestamp,
                Money.of(amount),
            )
        }
        return ActivityWindow(mappedActivities.toMutableList())
    }

    fun mapToJpaEntity(activity: Activity): ActivityJpaEntity =
        // EVALUATOR: use NAMED arguments for all 6 ctor params (id =, timestamp =,
        // ownerAccountId =, sourceAccountId =, targetAccountId =, amount =).
        // ActivityJpaEntity has 6 same-type-shape `Long?` fields and an easy
        // positional swap (source <-> target) would silently break behavior.
        // Named args are idiomatic Kotlin and make the mapping audit-friendly.
        ActivityJpaEntity(
            id = activity.id?.value,
            timestamp = activity.timestamp,
            ownerAccountId = activity.ownerAccountId.value,
            sourceAccountId = activity.sourceAccountId.value,
            targetAccountId = activity.targetAccountId.value,
            amount = activity.money.amount.toLong(),
        )
}
```

Key conversion points:
- The `activity.getId() == null ? null : activity.getId().getValue()` Java ternary collapses to `activity.id?.value` (Kotlin safe call).
- The `for`-loop in `mapToActivityWindow` becomes `activities.map { ... }`. The result is wrapped in `.toMutableList()` because `ActivityWindow`'s primary ctor (from Sprint 2) takes `MutableList<Activity>`.
- `Money.subtract(...)` / `Money.of(...)` companion-object `@JvmStatic` factories from Sprint 2 are called directly (same JVM call shape as Java).
- // EVALUATOR: SUPERSEDED. The original draft said "`!!` will carry a single-line comment". The evaluator decision above replaces every `!!` with `requireNotNull(field) { "ActivityJpaEntity loaded without <field>" }` (and likewise for `AccountJpaEntity.id`). Net effect: zero `!!` in the mapper; seven `requireNotNull` calls; failure messages name the specific field. The JPA-contract reason for nullable `Long?` on the entity properties is unchanged; only the post-load assertion style changed.
- `activity.money.amount.toLong()` replaces `activity.getMoney().getAmount().longValue()`. `Money.amount` is `BigInteger`; Kotlin `toLong()` is the idiomatic equivalent of `BigInteger.longValue()`.
- `ActivityWindow(mappedActivities.toMutableList())` — `ActivityWindow` primary ctor takes `MutableList<Activity>` per Sprint 2.

### 4. `SpringDataAccountRepository.kt`

- **Top-level type kind:** `interface ... : JpaRepository<AccountJpaEntity, Long>`; `internal`.
- **Shape:**

```kotlin
internal interface SpringDataAccountRepository : JpaRepository<AccountJpaEntity, Long>
```

- One-line interface body (or empty `{}`), no methods.

### 5. `ActivityRepository.kt`

- **Top-level type kind:** `interface ... : JpaRepository<ActivityJpaEntity, Long>`; `internal`.
- **JPQL `@Query` strings preserved VERBATIM:**

```kotlin
internal interface ActivityRepository : JpaRepository<ActivityJpaEntity, Long> {

    @Query(
        "select a from ActivityJpaEntity a " +
            "where a.ownerAccountId = :ownerAccountId " +
            "and a.timestamp >= :since"
    )
    fun findByOwnerSince(
        @Param("ownerAccountId") ownerAccountId: Long,
        @Param("since") since: LocalDateTime,
    ): List<ActivityJpaEntity>

    @Query(
        "select sum(a.amount) from ActivityJpaEntity a " +
            "where a.targetAccountId = :accountId " +
            "and a.ownerAccountId = :accountId " +
            "and a.timestamp < :until"
    )
    fun getDepositBalanceUntil(
        @Param("accountId") accountId: Long,
        @Param("until") until: LocalDateTime,
    ): Long?

    @Query(
        "select sum(a.amount) from ActivityJpaEntity a " +
            "where a.sourceAccountId = :accountId " +
            "and a.ownerAccountId = :accountId " +
            "and a.timestamp < :until"
    )
    fun getWithdrawalBalanceUntil(
        @Param("accountId") accountId: Long,
        @Param("until") until: LocalDateTime,
    ): Long?
}
```

Key conversion points:
- JPQL strings copied byte-for-byte from the Java source (line-break / concatenation pattern preserved — Kotlin string concatenation via `+` is fine; alternative would be a triple-quoted raw string but the `+` form keeps the diff minimal and matches Java line-by-line).
- `getDepositBalanceUntil` / `getWithdrawalBalanceUntil` return `Long?` — JPQL `sum(...)` returns `null` when no rows match (the original Java method signature was `Long` boxed which is implicitly nullable in Java; the adapter's `orZero(...)` helper exists precisely because of this null possibility). Kotlin must declare it `Long?` to compile the null-handling path.
- `@Param("...")` annotations preserved verbatim. Kotlin parameter-name reflection works in modern Spring but the explicit `@Param` matches the Java source and is robust to compiler-flag changes.
- `findByOwnerSince` returns `List<ActivityJpaEntity>` (non-null — JPA query never returns null list; empty list possible).

### 6. `AccountPersistenceAdapter.kt`

- **Top-level type kind:** plain `class`; `internal`; `@PersistenceAdapter` preserved.
- **Implements:** `LoadAccountPort`, `UpdateAccountStatePort` (already Kotlin from Sprint 3).
- **Shape:**

```kotlin
@PersistenceAdapter
internal class AccountPersistenceAdapter(
    private val accountRepository: SpringDataAccountRepository,
    private val activityRepository: ActivityRepository,
    private val accountMapper: AccountMapper,
) : LoadAccountPort, UpdateAccountStatePort {

    override fun loadAccount(
        accountId: Account.AccountId,
        baselineDate: LocalDateTime,
    ): Account {
        val account = accountRepository.findById(accountId.value)
            .orElseThrow { EntityNotFoundException() }

        val activities = activityRepository.findByOwnerSince(
            accountId.value,
            baselineDate,
        )

        val withdrawalBalance = activityRepository.getWithdrawalBalanceUntil(
            accountId.value,
            baselineDate,
        ) ?: 0L

        val depositBalance = activityRepository.getDepositBalanceUntil(
            accountId.value,
            baselineDate,
        ) ?: 0L

        return accountMapper.mapToDomainEntity(
            account,
            activities,
            withdrawalBalance,
            depositBalance,
        )
    }

    override fun updateActivities(account: Account) {
        // EVALUATOR: `account.activityWindow.activities` will NOT compile.
        // `ActivityWindow` (Sprint 2 output, see Activitywindow.kt) declares
        // `getActivities()` as a Kotlin `fun`, not as a property — and
        // `private val activities` is the *private* backing field, not the
        // accessor. Kotlin synthetic-property syntax only applies to JAVA
        // getters, not to Kotlin-declared `fun getX()`. Use the explicit
        // call: `account.activityWindow.getActivities()`. (Alternatively, a
        // Sprint-2 follow-up could expose `activities` as a Kotlin `val`
        // property — but that change is out of scope here.)
        for (activity in account.activityWindow.getActivities()) {
            if (activity.id == null) {
                activityRepository.save(accountMapper.mapToJpaEntity(activity))
            }
        }
    }
}
```

Key conversion points:
- `@RequiredArgsConstructor` collapses into Kotlin primary-constructor `val` parameter injection.
- `@PersistenceAdapter` (from Sprint 1) is meta-annotated `@Component`; kotlin-spring plugin makes the class `open` automatically for CGLIB proxying.
- The Java `private Long orZero(Long value)` helper is **inlined as `?: 0L` Elvis** at the two call sites — idiomatic and removes a one-line helper.
- `.orElseThrow(EntityNotFoundException::new)` → `.orElseThrow { EntityNotFoundException() }` (Kotlin lambda for `Supplier`).
- `account.activityWindow.activities` — `ActivityWindow.getActivities()` per Sprint 2 returns `List<Activity>` as a Kotlin property; this reads cleanly without `get*()` calls.
  // EVALUATOR: WRONG. Sprint 2 made `getActivities()` a `fun`, and Kotlin
  // synthetic properties don't kick in for Kotlin-declared functions.
  // Use `account.activityWindow.getActivities()` (function call) instead.
  // The comment "without `get*()` calls" must be removed at implementation
  // time — and the for-loop in `updateActivities` uses the call form per
  // the code-block edit above.
- `activity.id == null` works directly on the nullable `Activity.id: ActivityId?` (Sprint 2 shape).
- `accountId.value` replaces `accountId.getValue()` — `AccountId.value: Long` is the data-class property.
- `import javax.persistence.EntityNotFoundException` preserved (Spring Boot 2.4.3 namespace).

## Acceptance checks

- [ ] `find src/main/java/io/reflectoring/buckpal/account/adapter/out -name '*.java'` → 0
- [ ] `find src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence -name '*.kt'` → exactly 6
- [ ] `grep -R "import lombok" src/main/kotlin/io/reflectoring/buckpal/account/adapter/out` → 0 matches
- [ ] `grep -E "(lateinit|@Autowired)" src/main/kotlin/io/reflectoring/buckpal/account/adapter/out -r` → 0 matches (scope-restricted anti-pattern grep)
- [ ] `grep -R "Optional<" src/main/kotlin/io/reflectoring/buckpal/account/adapter/out` → 0 matches (no new `Optional` introduced in scope; `Account.kt`'s `Optional` shim is out of scope)
- [ ] `grep -c "!!" src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapper.kt` → **exactly 0** (the EVALUATOR decision above replaced all `!!` with `requireNotNull { ... }` blocks — verify zero `!!` in the mapper)
- [ ] `grep -c "requireNotNull" src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountMapper.kt` → **exactly 7** (one for `account.id` in `mapToDomainEntity`; six for `activity.{id,ownerAccountId,sourceAccountId,targetAccountId,timestamp,amount}` in `mapToActivityWindow`)
- [ ] `grep -R "!!" src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/` → **0 matches** (no `!!` anywhere in scope after the requireNotNull decision)
- [ ] `grep '@PersistenceAdapter' src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapter.kt` → 1 match
- [ ] `grep '@Entity' src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt` → 1 match; same for `ActivityJpaEntity.kt`
- [ ] `grep '@Table(name = "account")' src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountJpaEntity.kt` → 1 match
- [ ] `grep '@Table(name = "activity")' src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt` → 1 match
- [ ] JPQL strings preserved **verbatim** — `grep -c 'select a from ActivityJpaEntity a' src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt` → 1; `grep -c 'select sum(a.amount) from ActivityJpaEntity a' src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt` → 2; combined-text check: each of the three JPQL strings appears character-for-character as in the Java source (modulo Kotlin `+` concatenation syntax)
- [ ] `grep -c '@Param(' src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityRepository.kt` → **6** (method 1: `ownerAccountId`+`since` = 2; method 2: `accountId`+`until` = 2; method 3: `accountId`+`until` = 2 → 6 total)
  // EVALUATOR: original draft said 7 — math error. The Java source has 2+2+2=6 `@Param` annotations.
- [ ] Property names preserved exactly: `grep -E 'var (ownerAccountId|sourceAccountId|targetAccountId|timestamp|amount): ' src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt | wc -l` → 5
- [ ] `JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home ./gradlew clean compileKotlin compileJava compileTestKotlin compileTestJava test` → BUILD SUCCESSFUL, 16/16 pass
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest"` → 2/2 pass
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"` → 1/1 pass (full Spring Boot context + H2 + `@Sql` fixture)
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` → pass (ArchUnit green; package paths preserved)
- [ ] `./gradlew check` → BUILD SUCCESSFUL (ArchUnit DependencyRuleTests + all tests green)
- [ ] kotlinc warnings on the 6 new files → 0 (verified via `./gradlew clean compileKotlin --info | grep -E '^w:|warning:'` → 0 matches)
- [ ] // EVALUATOR: ADDED — `./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest.loadsAccount"` → PASS. This test specifically exercises (a) Hibernate instantiating `ActivityJpaEntity` via the kotlin-jpa-synthesized no-arg ctor, (b) all three JPQL `@Query` methods on `ActivityRepository`, (c) `AccountMapper.mapToDomainEntity` (the `requireNotNull` block). If kotlin-jpa is misconfigured, this test fails fast with `InstantiationException`; the targeted run isolates the failure mode from the broader `./gradlew test`.
- [ ] // EVALUATOR: ADDED — Architectural-Integrity sanity grep: `grep -c '@PersistenceAdapter' src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapter.kt` → 1 AND `grep -R '@Component' src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapter.kt` → 0 (the `@PersistenceAdapter` marker must be the only stereotype on the class; ArchUnit's `Adapters` helper grep-equivalent rules look for `@PersistenceAdapter`, not `@Component`).
- [ ] // EVALUATOR: ADDED — `./gradlew test --tests "io.reflectoring.buckpal.archunit.*"` → green. Same coverage as `DependencyRuleTests` but explicitly names the helper-class suite; flags any drift in the adapter package layout.
- [ ] // EVALUATOR: ADDED — Code-Quality grep: `grep -R 'TODO\|FIXME\|XXX' src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/` → 0 matches. (Catches commented-out leftovers or deferred-work TODOs without sprint references.)

## Idiomatic Kotlin commitments

1. **JPA entities are plain `class`, not `data class`.** JPA requires mutable properties for lazy loading and Hibernate proxy generation; equality on identity (Hibernate's behavior) would conflict with `data class`'s structural equality. All entity properties are `var ...: T? = null` so kotlin-jpa can synthesize the no-arg ctor.
2. **Primary-constructor injection** in `AccountPersistenceAdapter` — `private val` for each dependency. No `@Autowired field`, no `lateinit var`.
3. **Nullable types over `Optional<>`.** `getDepositBalanceUntil` / `getWithdrawalBalanceUntil` return `Long?`. The adapter handles the null with Elvis (`?: 0L`), eliminating the Java `orZero` helper entirely.
4. **Functional collection ops.** `mapToActivityWindow` uses `activities.map { ... }` instead of a manual `for`-loop + `ArrayList`. Result wrapped in `.toMutableList()` to fit `ActivityWindow`'s primary-ctor signature.
5. **Safe-call + Elvis.** `activity.id?.value` in `mapToJpaEntity` replaces the Java `activity.getId() == null ? null : activity.getId().getValue()` ternary — a textbook Kotlin idiom.
6. **`internal` for package-private Java types** — `AccountJpaEntity`, `ActivityJpaEntity`, `AccountMapper`, `SpringDataAccountRepository`, `ActivityRepository`, `AccountPersistenceAdapter` all use `internal`. Spring's component scan + Spring Data JPA's repository scan both work on `internal` Kotlin classes (bytecode is `public final`; Spring ignores name mangling on internal members).
7. // EVALUATOR: ADDED — **`requireNotNull` over `!!` for JPA-entity post-load reads.** Per the rubric (line 99-101) `!!` is allowed with a one-line comment, but `requireNotNull(field) { "JPA entity loaded without <field>" }` is more idiomatic, fails with a named message, and bunches well at the top of a `map { }` lambda. Zero `!!` in scope; 7 `requireNotNull` calls in `AccountMapper.kt`.
8. // EVALUATOR: ADDED — **Named arguments for `ActivityJpaEntity` ctor calls.** Six same-type-shape `Long?` parameters → positional invocation is a foot-gun (silent source/target swap). `mapToJpaEntity` uses named args.

## Risks specific to this sprint

1. **kotlin-jpa no-arg ctor synthesis.** Without the plugin Hibernate cannot instantiate entities reflectively. The plugin IS applied in `build.gradle` (`org.jetbrains.kotlin.plugin.jpa` v1.6.21, line 6). Verification: the `@Sql`-driven `AccountPersistenceAdapterTest.loadsAccount` test loads rows via Hibernate; if the no-arg ctor is missing, the test fails fast with `InstantiationException`.
2. **kotlin-spring `open` synthesis on `@PersistenceAdapter`.** `@PersistenceAdapter` is meta-annotated `@Component` (Sprint 1's `PersistenceAdapter.kt` carries `@Component`). kotlin-spring plugin opens the class for CGLIB. **No `@Transactional` is in scope for this sprint** (no transactional methods on the adapter), so CGLIB is not strictly required, but `kotlin-spring` will still open the class — a no-op concern, listed for completeness.
3. **Hibernate naming strategy for column names.** Default Spring Boot 2.4.3 strategy maps camelCase property names → snake_case columns (`ownerAccountId` → `owner_account_id`). The `@Sql` fixtures (both `AccountPersistenceAdapterTest.sql` and `SendMoneySystemTest.sql`) use snake_case column names, confirming the active strategy. Kotlin property names MUST stay camelCase verbatim — renaming would silently break the fixture inserts.
4. **JPQL references entity simple class name.** `ActivityJpaEntity` appears in all three `@Query` strings. The Kotlin class MUST be named `ActivityJpaEntity` (not `Activity` or `ActivityEntity`). The Kotlin file name MUST be `ActivityJpaEntity.kt` for tooling consistency.
5. **`@Sql` fixture insert order — `SendMoneySystemTest.sql`.** Inserts `account` rows before `activity` rows; activity references account FK semantically (no actual FK constraint in the schema, but the test assumes both exist). No change in behavior — the fixture is unmodified — but listed because the adapter's full-stack test depends on it.
6. **`AccountId.value: Long` unboxing.** `AccountId(account.id!!)` requires `account.id: Long?` to be non-null. The `!!` is safe at this point — Hibernate populates `@Id` on load — but the assertion is the only one in the adapter file. Considered acceptable per rubric's "explained `!!`" allowance (one-line comment required).
7. **`Money.amount: BigInteger.toLong()` vs Java `.longValue()`.** Both truncate silently if the value exceeds `Long.MAX_VALUE`. The H2 column is `Long` (max ~9.2e18); transferred amounts in fixtures are 500/1000. No risk in practice.
8. **`activityRepository.count()` in `updatesActivities` test.** Counts rows in the activity table. Currently passes because `@DataJpaTest` rolls back between tests and `updatesActivities` doesn't use `@Sql`. After conversion, behavior must be identical — `activityRepository` is a Kotlin `interface` extending `JpaRepository<>` so `count()` is inherited unchanged.
9. **Test still references `accountMapper.mapToJpaEntity(...)` indirectly via `activityRepository.findAll().get(0).getAmount()`.** `getAmount()` is auto-generated by Kotlin from `var amount: Long?` — Java callers see a `Long getAmount()` signature returning a possibly-null boxed Long. The test asserts `isEqualTo(1L)` — AssertJ unboxes; null would NPE. Hibernate guarantees the value is populated after save.

## Out of scope

- Root Spring config (`BuckPalApplication`, `BuckPalConfiguration`, `BuckPalConfigurationProperties`) — Sprint 7.
- Test conversion (`AccountPersistenceAdapterTest`, `SendMoneySystemTest`, all other `.java` tests) — Sprint 8.
- Removing `Account.kt`'s `Optional<AccountId>` shim — deferred to Sprint 9 (after Sprint 8 converts the Java tests that consume the Optional form).
- Removing Lombok from `build.gradle` dependencies — Sprint 9 cleanup.
- Switching `build.gradle` to `build.gradle.kts` — Sprint 9 optional.

## Implementation order

1. Read each Java file again immediately before conversion.
2. Create the 6 `.kt` files **in dependency order**:
   1. `AccountJpaEntity.kt` (no deps on other in-scope files).
   2. `ActivityJpaEntity.kt` (no deps on other in-scope files).
   3. `SpringDataAccountRepository.kt` (depends on `AccountJpaEntity`).
   4. `ActivityRepository.kt` (depends on `ActivityJpaEntity`).
   5. `AccountMapper.kt` (depends on both entity classes + domain).
   6. `AccountPersistenceAdapter.kt` (depends on all five above + Sprint 3 ports + Sprint 1 `@PersistenceAdapter`).
3. Run `./gradlew compileKotlin` after creating all 6 `.kt` files — must succeed before any deletion.
4. Delete the 6 `.java` files.
5. Run `./gradlew compileJava compileTestJava` — remaining Java consumers (root + tests) must still compile against the new Kotlin code. Specifically:
   - `AccountPersistenceAdapterTest.java` references `AccountPersistenceAdapter`, `AccountMapper`, `ActivityRepository`, `ActivityJpaEntity` — all by import; their Kotlin replacements must export the same JVM symbols (`getAmount()` etc.).
   - `BuckPalConfiguration.java` does NOT reference any persistence-adapter type directly (only `MoneyTransferProperties`); no risk there.
6. Run `./gradlew test` — all 16 tests must pass.
7. Targeted re-runs: `AccountPersistenceAdapterTest` (2/2), `SendMoneySystemTest` (1/1), `DependencyRuleTests` (2/2).
8. `./gradlew check` → green.
9. `./gradlew clean compileKotlin --info | grep -E '^w:|warning:'` → 0 matches.
10. Write `.claude/harness/workspace/handoffs/sprint-06-handoff.md`.
11. Commit: `feat(kotlin): sprint 6 — convert persistence adapter to Kotlin`.

## Self-check (Generator, before writing handoff)

- [ ] All 6 `.kt` files compile.
- [ ] All 6 `.java` files deleted.
- [ ] `find src/main/java/io/reflectoring/buckpal/account/adapter/out -name '*.java'` → 0
- [ ] `find src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence -name '*.kt'` → 6
- [ ] `./gradlew clean compileKotlin compileJava compileTestKotlin compileTestJava test` → BUILD SUCCESSFUL, 16/16 pass
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest"` → 2/2 pass
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"` → 1/1 pass
- [ ] `./gradlew check` → ArchUnit green
- [ ] No `import lombok`, no `lateinit`, no `@Autowired`, no `Optional<` in the 6 new files
- [ ] Zero `!!` in `AccountMapper.kt` (and zero anywhere else in scope) — all non-null assertions are written as `requireNotNull(field) { "<entity> loaded without <field>" }` per the EVALUATOR decision.
- [ ] `@Entity`, `@Table`, `@PersistenceAdapter`, `@Component`, `@Query`, `@Param` annotations preserved exactly
- [ ] JPQL strings preserved verbatim (text-compare against the Java source)
- [ ] Class simple names preserved: `AccountJpaEntity`, `ActivityJpaEntity`, `AccountMapper`, `SpringDataAccountRepository`, `ActivityRepository`, `AccountPersistenceAdapter`
- [ ] All 6 classes are `internal` (matching Java package-private)
- [ ] 0 kotlinc warnings on the new files
- [ ] `git status` — only in-scope files modified (6 java deleted, 6 kt added, plus contract/handoff/review meta-files)
