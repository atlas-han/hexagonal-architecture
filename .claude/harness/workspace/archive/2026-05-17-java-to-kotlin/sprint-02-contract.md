STATUS: AGREED
# Sprint 2 Contract — `account/domain/`

**Status:** AGREED (Evaluator: contract reviewed 2026-05-17; all 5 priority items verified against Java sources and callers)
**Generator:** main session
**Sprint goal (from spec):** Convert the domain core (4 source files
containing 6 types: Money, Account+AccountId, Activity+ActivityId,
ActivityWindow).

## Files in scope

Production (4 .java → 4 .kt):
- `src/main/java/io/reflectoring/buckpal/account/domain/Money.java` → delete
- `src/main/java/io/reflectoring/buckpal/account/domain/Account.java` → delete
- `src/main/java/io/reflectoring/buckpal/account/domain/Activity.java` → delete
- `src/main/java/io/reflectoring/buckpal/account/domain/ActivityWindow.java` → delete
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/Money.kt` → create
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/Account.kt` → create
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/Activity.kt` → create
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindow.kt` → create

Nothing else may be touched. The 9 remaining `.java` files under
`account/**` and `buckpal/**` continue to call into these types and
MUST keep compiling.

## Conversion targets

| Java file | Kotlin type | Notes |
|-----------|-------------|-------|
| `Money.java` | `data class Money(val amount: BigInteger)` | companion object with `@JvmField val ZERO`, `@JvmStatic fun of/add/subtract`; instance ops `plus`/`minus`/`negate` as `operator fun` where possible, regular `fun` otherwise |
| `Account.java` | `class Account private constructor(...)` | nullable `id`, companion `withId`/`withoutId`, **`fun getId(): Optional<AccountId>` shim for Java callers** |
| `Account.AccountId` (nested) | `data class AccountId(val value: Long)` | nested inside Account |
| `Activity.java` | `data class Activity(...)` | primary 6-arg ctor (id first, nullable), secondary 5-arg ctor without id |
| `Activity.ActivityId` (nested) | `data class ActivityId(val value: Long)` | nested inside Activity |
| `ActivityWindow.java` | `class ActivityWindow` | two ctors (List + vararg); `getActivities(): List` returns read-only view |

## Acceptance checks

- [ ] `find src/main/java/io/reflectoring/buckpal/account/domain -name '*.java'` → 0
- [ ] `find src/main/kotlin/io/reflectoring/buckpal/account/domain -name '*.kt'` → 4
- [ ] `grep -R "import lombok" src/main/kotlin/io/reflectoring/buckpal/account/domain` → empty
- [ ] `grep -R "Optional" src/main/kotlin/io/reflectoring/buckpal/account/domain` → exactly **1 file** containing `Optional` (Account.kt, for the `getId()` shim). No other domain file.
- [ ] `grep -R "@JvmStatic" src/main/kotlin/io/reflectoring/buckpal/account/domain/Money.kt` → at least 3 (`of`, `add`, `subtract`)
- [ ] `grep "@JvmField" src/main/kotlin/io/reflectoring/buckpal/account/domain/Money.kt` → at least 1 (`ZERO`)
- [ ] `grep "@JvmStatic" src/main/kotlin/io/reflectoring/buckpal/account/domain/Account.kt` → at least 2 (`withId`, `withoutId`)
- [ ] `grep -Rn "!!" src/main/kotlin/io/reflectoring/buckpal/account/domain | grep -v ".gitkeep"` — for each match the *next line above* must contain a `//` comment explaining why null is impossible. Acceptable use cases (per Idiomatic Kotlin criteria): `id!!` inside `Account.withdraw`/`Account.deposit` where the precondition is that a persisted Account has an id.
- [ ] `JAVA_HOME=… ./gradlew clean compileKotlin compileJava compileTestJava compileTestKotlin` → BUILD SUCCESSFUL (Java callers still compile against the new Kotlin types)
- [ ] `JAVA_HOME=… ./gradlew test` → 16/16 pass
- [ ] `JAVA_HOME=… ./gradlew test --tests "io.reflectoring.buckpal.account.domain.*"` → pass (the dedicated domain tests)
- [ ] `JAVA_HOME=… ./gradlew test --tests "io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest"` → pass (exercises Activity, Account, Money intensely)
- [ ] `JAVA_HOME=… ./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest"` → pass (exercises AccountMapper → Account.withId / new Activity / Money.subtract / Money.of)
- [ ] `JAVA_HOME=… ./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` → pass (ArchUnit unchanged)
- [ ] kotlinc warnings on the 4 new files → 0 (use the Sprint-1 recipe)

## Idiomatic Kotlin commitments

### Money

- `data class Money(val amount: BigInteger)`. BigInteger.equals is
  scale-stable so data-class equality is safe (no scale mismatch like
  BigDecimal would have).
- Companion object:
  - `@JvmField val ZERO = Money(BigInteger.ZERO)` — directly accessible as
    `Money.ZERO` from Java. `@JvmField` is required (otherwise Java sees
    `Money.Companion.getZERO()` which breaks existing `Money::add` /
    `Money.ZERO` references in `ActivityWindow.calculateBalance` etc.).
    Note: `@JvmField` requires `val` and a public initializer — both fine.
  - `@JvmStatic fun of(value: Long): Money`
  - `@JvmStatic fun add(a: Money, b: Money): Money`
  - `@JvmStatic fun subtract(a: Money, b: Money): Money`
  Without `@JvmStatic`, Java's `Money::add` method reference and
  `Money.add(...)` direct calls would not compile.
- Instance methods (preserve original signatures for Java callers):
  - `operator fun plus(money: Money): Money`
  - `operator fun minus(money: Money): Money`
  - `fun negate(): Money` — **NOT** `operator fun unaryMinus()` because
    Java callers do `.negate()` (Java doesn't support unary minus on
    objects).
  - `fun isPositiveOrZero()`, `isNegative()`, `isPositive()`,
    `isGreaterThanOrEqualTo(money)`, `isGreaterThan(money)`.
- The Kotlin `data class`-generated `equals`/`hashCode`/`toString` matches
  Lombok's `@Value` semantically.

### Account.AccountId / Activity.ActivityId

- Each is a nested `data class XxxId(val value: Long)`. Stays nested.
- Java sees `new Account.AccountId(123L)` and `accountId.getValue()` (the
  `val value` Kotlin property generates `getValue()` automatically). ✓
- **Do not** use `@JvmInline value class` here — the JVM erasure of nested
  value classes is awkward and we lose Java-friendly `new AccountId(123L)`.

### Account

```kotlin
class Account private constructor(
    @get:JvmName("getIdOrNull")
    val id: AccountId?,
    val baselineBalance: Money,
    val activityWindow: ActivityWindow,
) {
    fun getId(): Optional<AccountId> = Optional.ofNullable(id)
    fun calculateBalance(): Money = ...
    fun withdraw(money: Money, targetAccountId: AccountId): Boolean { ... }
    fun deposit(money: Money, sourceAccountId: AccountId): Boolean { ... }
    private fun mayWithdraw(money: Money): Boolean = ...
    companion object {
        @JvmStatic fun withoutId(...): Account = ...
        @JvmStatic fun withId(...): Account = ...
    }
    data class AccountId(val value: Long)
}
```

- **Critical:** `@get:JvmName("getIdOrNull")` on the `id` property so the
  Kotlin-synthesized `getId()` doesn't collide with the manually-written
  `fun getId(): Optional<AccountId>`. Java callers continue to call
  `.getId().orElseThrow(...)` (the `Optional` form).
- Kotlin callers use `account.id` (nullable property) for the direct
  nullable read, and `account.getId()` for the `Optional` wrapper.
- `withdraw` and `deposit` use `id!!` for the owner account id with a
  one-line comment:
  ```
  // id is guaranteed non-null on persisted accounts; withdraw is never
  // called on an unpersisted Account (Account.withoutId is only used in
  // the persistence-adapter factory path before save).
  val ownerId = id!!
  ```
  This is the exact use case the IK criterion allows for `!!`.

### Activity

```kotlin
data class Activity(
    val id: ActivityId?,
    val ownerAccountId: Account.AccountId,
    val sourceAccountId: Account.AccountId,
    val targetAccountId: Account.AccountId,
    val timestamp: LocalDateTime,
    val money: Money,
) {
    constructor(
        ownerAccountId: Account.AccountId,
        sourceAccountId: Account.AccountId,
        targetAccountId: Account.AccountId,
        timestamp: LocalDateTime,
        money: Money,
    ) : this(null, ownerAccountId, sourceAccountId, targetAccountId, timestamp, money)

    data class ActivityId(val value: Long)
}
```

- Primary 6-arg ctor matches Java's `@Value @RequiredArgsConstructor` (id
  first, all final).
- Secondary 5-arg ctor delegates to primary with `id = null` — matches
  the hand-written Java ctor. Both Java call shapes (with id and without)
  continue to work.
- `id` is the **first** parameter, not last, to preserve byte-for-byte
  Java call shape from `AccountMapper.mapToActivityWindow` which does
  `new Activity(new ActivityId(...), new AccountId(...), ...)`.
- No `@JvmOverloads` — the explicit secondary constructor is clearer for
  this case.

### ActivityWindow

```kotlin
class ActivityWindow {
    private val activities: MutableList<Activity>
    
    constructor(activities: MutableList<Activity>) { this.activities = activities }
    constructor(vararg activities: Activity) { this.activities = activities.toMutableList() }
    
    fun getStartTimestamp(): LocalDateTime = ...
    fun getEndTimestamp(): LocalDateTime = ...
    fun calculateBalance(accountId: Account.AccountId): Money = ...
    fun getActivities(): List<Activity> = Collections.unmodifiableList(activities)
    fun addActivity(activity: Activity) { activities.add(activity) }
}
```

- Two constructors (preserving Java call shapes — Java callers pass
  `List<Activity>` or `Activity...`).
- The first constructor keeps the **same list reference** (no defensive
  copy) — this matches the Java behavior; downstream mutations of the
  same list would still be visible. The Activity vararg ctor wraps in a
  new ArrayList (semantic match with Java).
- `getActivities()` returns `Collections.unmodifiableList(activities)`
  preserving Java's exact return type and immutability.
- **No `data class`** — ActivityWindow holds mutable state; equality based
  on mutable list reference would be misleading. This is a deliberate
  divergence from `data class` use.

## Out of scope

- Any callers of these types under `application/`, `adapter/`, `BuckPal*`.
- Removing the `Optional<AccountId>` shim from `Account.getId()` — that
  cleanup belongs to Sprint 4 (services) once all callers are Kotlin.
- Test source conversion (Sprint 8).
- Replacing `Money.add` / `Money.subtract` static factories with operator
  uses at call sites — same reason; deferred until callers are Kotlin.

## Implementation order

1. Create the 4 `.kt` files. Compile-check incrementally with
   `./gradlew compileKotlin`.
2. Once Kotlin compiles, delete the 4 `.java` files in the same directory.
3. Run `./gradlew compileJava` to confirm all 25 remaining Java files
   still compile against the new Kotlin domain types.
4. Run the full self-check (`clean compileKotlin compileJava
   compileTestJava compileTestKotlin test`).
5. Targeted runs: `account.domain.*`, `SendMoneyServiceTest`,
   `AccountPersistenceAdapterTest`, `DependencyRuleTests`.
6. Write handoff.

## Risks specific to this sprint

1. **`Account.getId()` Optional shim collision** with synthesized
   `getId(): AccountId?`. Resolution: `@get:JvmName("getIdOrNull")` on the
   `id` property. Verify the produced bytecode exposes both names: use
   `javap -p build/classes/kotlin/main/io/reflectoring/buckpal/account/domain/Account.class`
   to confirm `public final Optional<AccountId> getId()` AND
   `public final AccountId getIdOrNull()` both exist.
2. **`Money.ZERO` static-field access from Java.** Without `@JvmField`,
   `ActivityWindow.calculateBalance()` (still Java in this sprint) would
   fail to compile because `Money.ZERO` would resolve to
   `Money.Companion.getZERO()`. **Test:** `compileJava` after Money.kt is in
   place must succeed without touching ActivityWindow.java.
3. **`Money::add` method reference from Java.** Without `@JvmStatic`, the
   reference resolves to `Companion.add` which Java can't take a reference
   to via `Money::add`. Same compileJava check covers this.
4. **`Activity` constructor field order** — primary ctor's id must be the
   first param (not last) so the AccountMapper's
   `new Activity(new ActivityId(...), ...)` call shape continues to work.
   Verify: `compileJava` succeeds after Activity.kt is in place.
5. **`@NonNull` parameter validation** from Lombok is replaced by Kotlin's
   non-null types (NPE at the JVM boundary). Behavior preserved — any
   Java caller passing null will get an NPE at the same call site.
6. **`Money.of(0L) == Money(BigInteger.ZERO)` equality.** `data class`
   equality compares `amount: BigInteger`. `BigInteger.valueOf(0)` is the
   cached `BigInteger.ZERO`. So `Money.of(0L).amount === BigInteger.ZERO`
   and equality holds. ✓
7. **`ActivityWindow` no `data class`.** If a future caller relied on
   `data class`-generated equals (unlikely — the original Java didn't have
   it either), this would be a divergence. Preserved-by-default since
   Lombok generated no equals for it.

## Self-check (Generator, before writing handoff)

- [ ] All 4 `.kt` files compile.
- [ ] All 4 `.java` files in scope deleted.
- [ ] `./gradlew compileJava` passes (Java callers happy).
- [ ] `./gradlew compileTestJava` passes (Java tests happy).
- [ ] `./gradlew test` → 16/16 green.
- [ ] `javap -p` on `Account.class` shows both `getId(): Optional<AccountId>` AND `getIdOrNull(): AccountId`.
- [ ] No `import lombok` in any new Kotlin file.
- [ ] Each `!!` has a one-line comment immediately above.
- [ ] 0 kotlinc warnings.
