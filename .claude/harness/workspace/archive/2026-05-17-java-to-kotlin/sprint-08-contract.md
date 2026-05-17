STATUS: AGREED

<!-- EVALUATOR (Phase A): Reviewed against product-spec.md Sprint 8 section,
     evaluator.md checklist, and the four rubric criteria in
     kotlin-conversion.md. Sampled AccountTestData.java, HexagonalArchitecture.java,
     SendMoneyServiceTest.java, SendMoneySystemTest.java, ArchitectureElement.java,
     Adapters.java, DependencyRuleTests.java. The 14-file scope matches `find
     src/test/java -name '*.java' | wc -l` = 14. Both design decisions resolved
     below; small additional checks injected. STATUS: AGREED. -->

# Sprint 8 Contract — Test sources

**Status:** DRAFT (awaiting Evaluator Phase A review)
**Generator:** main session
**Sprint goal (from spec):** Convert all 14 test source files to Kotlin
(JUnit5 + Mockito + Spring Boot Test + ArchUnit), without altering any test
signature or assertion, and without introducing MockK.

## Files in scope

Test sources (14 .java → 14 .kt). Package paths preserved verbatim; only the
root source directory flips from `src/test/java` to `src/test/kotlin`.

| Java file | Kotlin equivalent |
|-----------|-------------------|
| `src/test/java/io/reflectoring/buckpal/BuckPalApplicationTests.java`                                              | `src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` |
| `src/test/java/io/reflectoring/buckpal/DependencyRuleTests.java`                                                  | `src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` |
| `src/test/java/io/reflectoring/buckpal/SendMoneySystemTest.java`                                                  | `src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` |
| `src/test/java/io/reflectoring/buckpal/common/AccountTestData.java`                                               | `src/test/kotlin/io/reflectoring/buckpal/common/AccountTestData.kt` |
| `src/test/java/io/reflectoring/buckpal/common/ActivityTestData.java`                                              | `src/test/kotlin/io/reflectoring/buckpal/common/ActivityTestData.kt` |
| `src/test/java/io/reflectoring/buckpal/archunit/Adapters.java`                                                    | `src/test/kotlin/io/reflectoring/buckpal/archunit/Adapters.kt` |
| `src/test/java/io/reflectoring/buckpal/archunit/ApplicationLayer.java`                                            | `src/test/kotlin/io/reflectoring/buckpal/archunit/ApplicationLayer.kt` |
| `src/test/java/io/reflectoring/buckpal/archunit/ArchitectureElement.java`                                         | `src/test/kotlin/io/reflectoring/buckpal/archunit/ArchitectureElement.kt` |
| `src/test/java/io/reflectoring/buckpal/archunit/HexagonalArchitecture.java`                                       | `src/test/kotlin/io/reflectoring/buckpal/archunit/HexagonalArchitecture.kt` |
| `src/test/java/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.java`                       | `src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` |
| `src/test/java/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.java`        | `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` |
| `src/test/java/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.java`                     | `src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` |
| `src/test/java/io/reflectoring/buckpal/account/domain/AccountTest.java`                                           | `src/test/kotlin/io/reflectoring/buckpal/account/domain/AccountTest.kt` |
| `src/test/java/io/reflectoring/buckpal/account/domain/ActivityWindowTest.java`                                    | `src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt` |

Out-of-scope (explicitly **not touched**):

- `src/main/kotlin/**` — production code is frozen for this sprint. In
  particular, `Account.kt`'s `Optional<AccountId>` shim (Sprint 2/4
  transitional API) stays as-is. The Mockito stub in `SendMoneyServiceTest`
  drives `account.getId(): Optional<AccountId>` and the dual `id`/`getId()`
  surface is what allows that. Removal of the shim is **Sprint 9** work.
- `build.gradle` — Lombok dependency removal and `apply plugin: 'java'`
  removal are Sprint 9.
- `src/test/resources/**` — the two `.sql` fixtures
  (`SendMoneySystemTest.sql`, `AccountPersistenceAdapterTest.sql`) and their
  directory layout are unchanged. `@Sql("SendMoneySystemTest.sql")` and
  `@Sql("AccountPersistenceAdapterTest.sql")` use relative resource
  resolution from the test class's package; both classes keep the same
  package after conversion, so the fixture paths continue to resolve via
  `src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql` and
  `src/test/resources/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.sql`.

## Spec-derived guardrails (DO NOT violate)

1. **Mockito stays.** No `io.mockk` imports, no MockK DSL. The two Mockito
   test files (`SendMoneyServiceTest`, `SendMoneyControllerTest`) continue
   to use `org.mockito.Mockito`, `org.mockito.BDDMockito`, and Spring's
   `@MockBean`.
2. **`io.reflectoring.reviewapp` strings preserved verbatim.** Both
   `DependencyRuleTests.testPackageDependencies` (lines 36, 39, 41 of the
   original Java) and `ArchitectureElement.denyDependency` (lines 27, 29 of
   the original Java) use the literal package strings
   `"io.reflectoring.reviewapp.domain.."`,
   `"io.reflectoring.reviewapp.application.."`, and
   `"io.reflectoring.reviewapp.."` — these are intentional book placeholders
   and **must appear character-for-character** in the Kotlin output. The
   `denyDependency` function's `fromPackageName` / `toPackageName`
   parameters are unused-by-design in the original Java — the placeholders
   ignore them. Kotlin preserves that exactly (parameters present in the
   signature, unused in the body).
3. **`@Sql` resource paths kept verbatim.** `@Sql("SendMoneySystemTest.sql")`
   and `@Sql("AccountPersistenceAdapterTest.sql")` — relative names, no
   leading slash, identical to the Java source.
4. **No test signature or assertion change.** The 16 `@Test` methods stay
   1:1 (names, parameter lists, return-shape `void`/`Unit`, every `assertThat`
   / `then` / `should` call preserved). The Generator may only re-shape
   syntax (Java → Kotlin idioms).
5. **JUnit 5 (`org.junit.jupiter.api.Test`)**. The Java sources already use
   Jupiter (`import org.junit.jupiter.api.Test`); the Kotlin conversions
   stay on Jupiter. No JUnit 4 import. `@ExtendWith(SpringExtension::class)`
   in `BuckPalApplicationTests` is **preserved verbatim** even though
   `@SpringBootTest` implies `SpringExtension` — preserving structure is
   higher priority than minor cleanup.
<!-- EVALUATOR: decision: Decision 1 (test-data builder shape) — CONFIRMED:
     builder class with `withX(...) = apply { ... }` chain. Rationale: the
     Java source (verified — AccountTestData.java lines 10-44, similarly
     ActivityTestData) is *already* a chainable builder, not @Builder/Lombok.
     The Kotlin apply-chain is therefore the literal one-to-one translation;
     every test body (AccountTest, ActivityWindowTest, AccountPersistenceAdapterTest,
     the seed in AccountTestData itself) keeps its
     `defaultX().withA(...).withB(...).build()` shape verbatim. The rubric's
     "Test data builders... Default args are simpler" line is a guideline,
     not a mandate; preserving test bodies is the higher-order constraint
     given the non-negotiable invariant "Modifying a test... is FAIL".
     Apply-chain is idiomatic Kotlin (apply returns receiver) and keeps the
     diff syntax-only. APPROVED. -->
6. **Builders → default-arg factory funs** (spec note: "Default args are
   simpler and require fewer call-site changes"). `AccountTestData` and
   `ActivityTestData` become Kotlin `object`s with a `defaultAccountBuilder()`
   / `defaultActivityBuilder()` chainable-builder class to preserve the
   fluent `defaultAccount().withAccountId(...).withMoney(...).build()`
   call shape used at **every** call site. Rationale: the test bodies of
   `AccountTest` (lines 14-24, 32-44, 53-66, 76-88), `ActivityWindowTest`
   (lines 14-17, 24-27, 38-50), and `AccountPersistenceAdapterTest`
   (lines 39-45) chain 3–5 `.withX(...)` calls per builder. Replacing the
   builder chain with named-arg factory functions would touch every test
   body, which the spec rules out ("Modifying a test to make it pass is
   FAIL"). The Kotlin equivalent of the Java `@Builder`-style fluent
   builder is a Kotlin `class` with `private var` fields and chainable
   `withX(...): AccountBuilder` methods. This keeps **every** call site
   compiling verbatim. The "default args" idiom from the spec applies to
   the **top-level seed** (`defaultAccount()`, `defaultActivity()`) — the
   seed values become the builder's initial property defaults.

## Conversion targets

### 1. `BuckPalApplicationTests.kt`

- **Top-level type kind:** plain `class`; default visibility (no modifier;
  Kotlin `internal` would mangle the JVM name and break Spring's classpath
  scan-based test discovery — the original Java was package-private so the
  closest equivalent is a no-modifier Kotlin class; JUnit 5 discovers it
  via classpath scanning, not reflection on name).
- **Shape:**

  ```kotlin
  package io.reflectoring.buckpal

  import org.junit.jupiter.api.Test
  import org.junit.jupiter.api.extension.ExtendWith
  import org.springframework.boot.test.context.SpringBootTest
  import org.springframework.test.context.junit.jupiter.SpringExtension

  @ExtendWith(SpringExtension::class)
  @SpringBootTest
  class BuckPalApplicationTests {

      @Test
      fun contextLoads() {
      }
  }
  ```

- 1 test method; body intentionally empty (the assertion is "the context
  refreshed successfully").

### 2. `DependencyRuleTests.kt`

- **Top-level type kind:** plain `class`; no modifier.
- Imports: `com.tngtech.archunit.core.importer.ClassFileImporter`,
  `io.reflectoring.buckpal.archunit.HexagonalArchitecture`,
  `org.junit.jupiter.api.Test`,
  `com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses`.
- **Preserved verbatim:** the literal strings
  `"io.reflectoring.reviewapp.domain.."`,
  `"io.reflectoring.reviewapp.application.."`,
  `"io.reflectoring.reviewapp.."` in `testPackageDependencies`. The
  surrounding `bounded-context` DSL chain in `validateRegistrationContextArchitecture`
  uses real strings (`"io.reflectoring.buckpal.account"`,
  `"io.reflectoring.buckpal.."`) — those are also preserved verbatim.
- 2 test methods (`validateRegistrationContextArchitecture`,
  `testPackageDependencies`).

### 3. `SendMoneySystemTest.kt`

- **Top-level type kind:** plain `class`; no modifier.
- `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` preserved.
- `lateinit var` for `@Autowired` fields: `restTemplate`, `loadAccountPort`.
  Rationale: Spring autowires after construction; the alternative
  (constructor injection on test classes) requires `@TestConstructor` or
  JUnit 5 + Spring's parameter resolver and is brittle here. The
  evaluator's anti-pattern grep on `src/main/kotlin` does not extend to
  `src/test/kotlin`; `lateinit var` for `@Autowired` test fields is the
  documented idiom (Spring Kotlin reference) — call it out in the handoff.
- **`@Sql("SendMoneySystemTest.sql")` preserved verbatim** on the
  `sendMoney` test method.
- `Money.minus` and `Money.plus` operator funs already exist on Kotlin
  `Money` (Sprint 2 output); the test's `initialSourceBalance.minus(transferredAmount())`
  call form is preserved as a method call (also legal in Kotlin via
  `operator fun`).
- The Java `private ResponseEntity whenSendMoney(...)` uses raw types — the
  Kotlin version uses `ResponseEntity<*>` (star-projection) for both the
  return type and the `HttpEntity<Void>` request body. The raw-type warning
  is suppressed by giving an explicit `Class<*>` argument (`Any::class.java`)
  matching `Object.class` in the original.
- 1 test method.

### 4. `AccountTestData.kt`

- **Top-level type kind:** Kotlin `object` (replaces `public class` with
  `public static defaultAccount()`); contains a nested `class AccountBuilder`.
- **`defaultAccount()` is a member function on the `object`** — `import
  io.reflectoring.buckpal.common.AccountTestData.defaultAccount` works
  exactly as for Java static imports because Kotlin compiles `object`
  members with `@JvmStatic`-equivalent semantics when the member is
  declared on the `object` itself **and** annotated with `@JvmStatic`.
  We **add `@JvmStatic`** to `defaultAccount()` so the
  `import static io.reflectoring.buckpal.common.AccountTestData.*` style
  used in `AccountTest`, `AccountPersistenceAdapterTest` continues working
  even if those callers had remained Java (defensive — they are converting
  this sprint). Kotlin call sites use `defaultAccount()` directly via a
  regular member-import.
- **Builder shape:** chainable class with `private var` fields and
  `withX(...): AccountBuilder` returning `this`. The `build(): Account`
  method calls `Account.withId(accountId, baselineBalance, activityWindow)`
  (the existing `@JvmStatic` factory from Sprint 2).
- **Initial values seeded by `defaultAccount()`:** `accountId = Account.AccountId(42L)`,
  `baselineBalance = Money.of(999L)`, `activityWindow = ActivityWindow(
  ActivityTestData.defaultActivity().build(),
  ActivityTestData.defaultActivity().build())`.
- The builder's three `private var` fields are typed
  `Account.AccountId`, `Money`, `ActivityWindow` (non-null) — they are
  always initialized by `defaultAccount()` before `build()`, so non-null
  is correct. (No `lateinit` needed; defaults are assigned at construction
  time inside `defaultAccount()`.)

  Shape sketch:
  ```kotlin
  object AccountTestData {

      @JvmStatic
      fun defaultAccount(): AccountBuilder = AccountBuilder(
          accountId = Account.AccountId(42L),
          baselineBalance = Money.of(999L),
          activityWindow = ActivityWindow(
              ActivityTestData.defaultActivity().build(),
              ActivityTestData.defaultActivity().build(),
          ),
      )

      class AccountBuilder internal constructor(
          private var accountId: Account.AccountId,
          private var baselineBalance: Money,
          private var activityWindow: ActivityWindow,
      ) {
          fun withAccountId(accountId: Account.AccountId): AccountBuilder = apply { this.accountId = accountId }
          fun withBaselineBalance(baselineBalance: Money): AccountBuilder = apply { this.baselineBalance = baselineBalance }
          fun withActivityWindow(activityWindow: ActivityWindow): AccountBuilder = apply { this.activityWindow = activityWindow }
          fun build(): Account = Account.withId(accountId, baselineBalance, activityWindow)
      }
  }
  ```

### 5. `ActivityTestData.kt`

- **Top-level type kind:** Kotlin `object`; contains nested
  `class ActivityBuilder`.
- **Initial values seeded by `defaultActivity()`:** `id = null`,
  `ownerAccountId = Account.AccountId(42L)`,
  `sourceAccountId = Account.AccountId(42L)`,
  `targetAccountId = Account.AccountId(41L)`,
  `timestamp = LocalDateTime.now()`,
  `money = Money.of(999L)`.
- **`id` field is nullable** (`Activity.ActivityId?`); `withId(id: Activity.ActivityId?)`
  signature preserved — call site `defaultActivity().withId(null).withMoney(Money.of(1L)).build()`
  in `AccountPersistenceAdapterTest.updatesActivities` requires the
  nullable signature.
- `@JvmStatic` on `defaultActivity()` (same rationale as `defaultAccount()`).
- `build()` returns `Activity(id, ownerAccountId, sourceAccountId,
  targetAccountId, timestamp, money)` — calls the 6-arg primary constructor
  of the existing Kotlin `data class Activity`.

  Shape sketch:
  ```kotlin
  object ActivityTestData {

      @JvmStatic
      fun defaultActivity(): ActivityBuilder = ActivityBuilder(
          id = null,
          ownerAccountId = Account.AccountId(42L),
          sourceAccountId = Account.AccountId(42L),
          targetAccountId = Account.AccountId(41L),
          timestamp = LocalDateTime.now(),
          money = Money.of(999L),
      )

      class ActivityBuilder internal constructor(
          private var id: Activity.ActivityId?,
          private var ownerAccountId: Account.AccountId,
          private var sourceAccountId: Account.AccountId,
          private var targetAccountId: Account.AccountId,
          private var timestamp: LocalDateTime,
          private var money: Money,
      ) {
          fun withId(id: Activity.ActivityId?): ActivityBuilder = apply { this.id = id }
          fun withOwnerAccount(accountId: Account.AccountId): ActivityBuilder = apply { this.ownerAccountId = accountId }
          fun withSourceAccount(accountId: Account.AccountId): ActivityBuilder = apply { this.sourceAccountId = accountId }
          fun withTargetAccount(accountId: Account.AccountId): ActivityBuilder = apply { this.targetAccountId = accountId }
          fun withTimestamp(timestamp: LocalDateTime): ActivityBuilder = apply { this.timestamp = timestamp }
          fun withMoney(money: Money): ActivityBuilder = apply { this.money = money }
          fun build(): Activity = Activity(id, ownerAccountId, sourceAccountId, targetAccountId, timestamp, money)
      }
  }
  ```

### 6. `Adapters.kt` (archunit helper)

- **Top-level type kind:** plain `class Adapters : ArchitectureElement(basePackage)`;
  package-default visibility (no modifier). Same primary-ctor signature as
  the Java `Adapters(HexagonalArchitecture, String)` (package-private
  ctor in Java → `internal constructor` in Kotlin **so visibility matches
  the Java surface**).
  - **Decision (binding):** the constructor is `internal constructor` to
    mirror Java package-private; `HexagonalArchitecture.kt` (same package)
    invokes it. `DependencyRuleTests.kt` (different package
    `io.reflectoring.buckpal`) does **not** invoke `Adapters(...)` directly
    — only via the fluent DSL through `HexagonalArchitecture` — so
    `internal` does not block it.
- **Methods:** `outgoing`, `incoming` (public; chainable `Adapters`),
  `and()` (public; `HexagonalArchitecture`), `allAdapterPackages()`
  (package-private → `internal`), `getBasePackage()` (package-private →
  `internal`), `dontDependOnEachOther`, `doesNotDependOn`,
  `doesNotContainEmptyPackages` (all package-private → `internal`).
- **Fields:** `incomingAdapterPackages: MutableList<String>` and
  `outgoingAdapterPackages: MutableList<String>` — declared as `private val`
  initialized to `mutableListOf()`. `parentContext` is `private val` from
  primary ctor.
- **`getBasePackage()` collision:** `ArchitectureElement.basePackage` is a
  property; Kotlin auto-generates a `getBasePackage()` JVM accessor.
  Adding a Kotlin `fun getBasePackage(): String = basePackage` would
  conflict at the JVM level. **Decision (binding):** expose
  `getBasePackage()` as a Kotlin **function** by renaming the call site
  in `HexagonalArchitecture.kt`'s `check(...)` to `this.adapters.basePackage`
  property access (Kotlin's synthetic property syntax works because
  `basePackage` is a Kotlin property, not a Kotlin `fun`). No additional
  function declaration needed; the `getBasePackage` Java method
  effectively collapses into the property accessor on
  `ArchitectureElement`. // Generator: verify `basePackage` is exposed as
  `val basePackage: String` (no visibility modifier inside same package)
  in `ArchitectureElement.kt`; `HexagonalArchitecture.check()`'s call site
  uses `this.adapters.basePackage` directly.

### 7. `ApplicationLayer.kt` (archunit helper)

- **Top-level type kind:** plain `class ApplicationLayer(basePackage: String, parentContext: HexagonalArchitecture) : ArchitectureElement(basePackage)`;
  public (the Java class was `public`).
- Same method-by-method conversion as `Adapters.kt`. `incomingPorts`,
  `outgoingPorts`, `services`, `and()`, `doesNotDependOn`,
  `incomingAndOutgoingPortsDoNotDependOnEachOther` are public;
  `allPackages()` and `doesNotContainEmptyPackages()` were package-private
  → `internal`.
- `incomingPortsPackages`, `outgoingPortsPackages`, `servicePackages` are
  `private val ... = mutableListOf<String>()`.

### 8. `ArchitectureElement.kt` (archunit helper)

- **Top-level type kind:** `abstract class ArchitectureElement(val basePackage: String)`;
  package-private in Java → `internal` in Kotlin. The `basePackage`
  primary-ctor `val` exposes the field used by `Adapters.getBasePackage()`
  via property syntax; the Java `final String basePackage;` was
  package-private — the Kotlin `val basePackage: String` defaults to
  `public` get, which is **wider** than Java's package-private but matches
  what the Java code effectively exposed (subclasses in the same package
  read the field; outside-package code cannot reach the constructor so the
  property is unreachable in practice).
- **Methods:**
  - `fullQualifiedPackage(relativePackage: String): String` —
    `internal fun` (was package-private).
  - `companion object`-hosted static helpers:
    - `denyDependency(fromPackageName: String, toPackageName: String, classes: JavaClasses)`
      — **placeholders preserved verbatim** in the body:
      `"io.reflectoring.reviewapp.domain.."` and
      `"io.reflectoring.reviewapp.application.."`. The two parameter names
      are kept (unused in the body, matching Java).
    - `denyAnyDependency(fromPackages: List<String>, toPackages: List<String>, classes: JavaClasses)`.
    - `matchAllClassesInPackage(packageName: String): String`.
  - `denyEmptyPackage(packageName: String)` — non-companion `internal fun`.
  - `denyEmptyPackages(packages: List<String>)` — non-companion `internal fun`.
  - `classesInPackage(packageName: String): JavaClasses` — `private fun`.
- `@JvmStatic` on the three companion-object helpers so that any
  cross-package caller using the simple-name form (none in scope, but
  defensive) keeps the same JVM symbol shape as the Java static.

### 9. `HexagonalArchitecture.kt` (archunit helper)

- **Top-level type kind:** `class HexagonalArchitecture(basePackage: String) : ArchitectureElement(basePackage)`;
  public.
- **`companion object`** with `@JvmStatic fun boundedContext(basePackage: String): HexagonalArchitecture`.
- **Members:**
  - `private var adapters: Adapters? = null` — late-assigned by
    `withAdaptersLayer(...)`. The DSL ensures `adapters` is non-null at
    `check(...)` time; use `!!` in `check()` with a one-line comment
    ("DSL guarantees adapters set before check"). Same for
    `applicationLayer`.
  - `private var configurationPackage: String? = null` — late-assigned by
    `withConfiguration(...)`.
  - `private val domainPackages: MutableList<String> = mutableListOf()`.
- **Methods (preserve names + visibilities):** `withAdaptersLayer`,
  `withDomainLayer`, `withApplicationLayer`, `withConfiguration`,
  `check(classes: JavaClasses)` — all `public`.
- **`domainDoesNotDependOnOtherPackages` is `private`.**
- **DSL preservation:** `check()` body invokes
  `this.adapters!!.doesNotContainEmptyPackages()`, etc. Acceptable per
  rubric: each `!!` carries a one-line comment "DSL guarantees adapters
  set before check()" (4 occurrences across `adapters!!` /
  `applicationLayer!!` / `configurationPackage!!`).
  - **Alternative (preferred if it does not bloat the file):** replace
    `!!` with `checkNotNull(adapters) { "withAdaptersLayer must be called before check" }`
    bound to local `val` at the top of `check()`. Generator picks one and
    flags the choice in the handoff. The acceptance check below permits
    either zero `!!` (preferred) or `!!`-with-comment.

<!-- EVALUATOR: decision: Decision 2 (HexagonalArchitecture.check() body) —
     OVERRIDE the "either accepted" wording. REQUIRED: use `checkNotNull(...)`
     bound to a local `val` for each of `adapters`, `applicationLayer`, and
     `configurationPackage` at the top of `check()`. Rationale: same pattern
     resolved in Sprint 6 (`requireNotNull`); `checkNotNull` carries an
     intent-revealing message and avoids the four-`!!`-with-comments noise.
     `!!` is technically permitted by the rubric *with* comment, but
     `checkNotNull` is cleaner *and* lets the smart-cast carry through the
     rest of `check()` so subsequent dereferences (e.g.
     `this.adapters.doesNotContainEmptyPackages()`) read naturally without
     repeated `!!`. UPDATE acceptance checks below: `grep -c '!!' src/test/kotlin/io/reflectoring/buckpal/archunit/HexagonalArchitecture.kt`
     must be 0. -->


### 10. `SendMoneyControllerTest.kt`

- **Top-level type kind:** plain `class`; no modifier.
- `@WebMvcTest(controllers = [SendMoneyController::class])` — Kotlin array
  literal `[...]` for the `Class<?>[]` annotation attribute.
- `@Autowired lateinit var mockMvc: MockMvc` and
  `@MockBean lateinit var sendMoneyUseCase: SendMoneyUseCase`. (Spring
  Kotlin idiom for `@MockBean` field injection on test classes.)
- `@Test fun testSendMoney()` — same name, body preserved.
- **Mockito `then(...).should().sendMoney(...)` chain preserved.** No
  conflict with Kotlin keywords because `then` (BDDMockito), `should`, and
  `sendMoney` are all method names.
- The Java `throws Exception` clause maps to no Kotlin throws clause
  (Kotlin doesn't have checked exceptions).
- `eq(new SendMoneyCommand(...))` becomes `eq(SendMoneyCommand(...))` —
  the `new` keyword is dropped.

### 11. `AccountPersistenceAdapterTest.kt`

- **Top-level type kind:** plain `class`; no modifier.
- `@DataJpaTest` and `@Import(AccountPersistenceAdapter::class, AccountMapper::class)`
  (Kotlin array literal omits braces when only one annotation argument is
  involved; here it's `@Import(...)` with a `KClass<*>[]` value).
- `@Autowired lateinit var adapterUnderTest: AccountPersistenceAdapter` and
  `@Autowired lateinit var activityRepository: ActivityRepository`.
- **Visibility shim:** `AccountPersistenceAdapter`, `AccountMapper`, and
  `ActivityRepository` are all `internal` Kotlin classes/interfaces
  (Sprint 6). Kotlin `internal` is bytecode-public but **the module-mangle
  rule** means cross-module references go through the mangled name (e.g.,
  `AccountPersistenceAdapter$module-name`). For the `test` source set in
  the same Gradle module, `internal` types are directly visible (no
  mangling fights) — `compileTestKotlin` shares the module with
  `compileKotlin`. **No production-code change required.** This is an
  active risk: confirmed via Sprint 6 review (`AccountPersistenceAdapterTest.java`
  imported them as Java package-private successfully under the existing
  `compileJava`+`compileTestJava` Kotlin/Java mixed setup; after this
  sprint converts the test to `.kt`, the Kotlin-to-Kotlin `internal`
  visibility is the standard mode).
- **`@Sql("AccountPersistenceAdapterTest.sql")` preserved verbatim.**
- 2 test methods (`loadsAccount`, `updatesActivities`).
- `defaultAccount().withBaselineBalance(Money.of(555L)).withActivityWindow(...)`
  call form is preserved by the chainable builder.
- `savedActivity.getAmount()` — the test calls a `getAmount()` Java-style
  accessor; on the new `ActivityJpaEntity.kt` (`var amount: Long?`) Kotlin
  exposes the JVM accessor as `getAmount()`. Kotlin call site uses
  `savedActivity.amount` (synthetic property). `isEqualTo(1L)` against
  `Long?` works (AssertJ unboxes).

### 12. `SendMoneyServiceTest.kt`

- **Top-level type kind:** plain `class`; no modifier.
- **Mockito stays, with the `Optional<AccountId>` stub preserved.** The
  three `private final` mock fields become `private val ... = Mockito.mock(T::class.java)`.
  `Mockito.mock(Class<T>)` is the Java-style call; Kotlin idiom would be
  `mock<T>()` (kotlin-mockito) but **MockK and kotlin-mockito are both
  out of scope**. The Java-shape call works directly in Kotlin.
- **`sendMoneyService`** initialized inline:
  `private val sendMoneyService = SendMoneyService(loadAccountPort, accountLock, updateAccountStatePort, moneyTransferProperties())`.
- **`given(account.getId()).willReturn(Optional.of(id))`** — preserved
  verbatim. `account.getId()` calls the Kotlin `Account.getId(): Optional<AccountId>`
  shim that Sprint 2 deliberately retained. The test exercises the
  Optional surface; Sprint 9 will collapse the shim and rewrite this stub
  to `given(account.id).willReturn(id)` (nullable).
- **`Mockito.when()` is NOT used** — the Java source uses BDD form
  (`given(...).willReturn(...)`, `then(...).should()...`) exclusively. So
  the `when` keyword collision in Kotlin is **not triggered** in any of
  these tests. No backtick `` `when` `` needed. The risk is listed for
  completeness in §Risks but does not appear in scope. (If a future
  reviewer ports a stub to the classical `when(...).thenReturn(...)` form,
  Kotlin requires `` Mockito.`when`(...).thenReturn(...) `` — but that is
  not happening in this sprint.)
- **`Mockito.mock(Account::class.java)` returns a non-null `Account`** that
  Mockito proxies via CGLIB. The Sprint 2 `Account` is `open` (`open class
  Account private constructor`), so Mockito can subclass it. The `getId()`
  method is also `open` (Sprint 2 made it `open fun getId(): Optional<AccountId>`).
  `withdraw`, `deposit`, `calculateBalance` are `open` (Sprint 2). ✓
- **ArgumentCaptor + `Account::getId` method reference** in
  `thenAccountsHaveBeenUpdated`: `.map(Account::getId).map(Optional::get)`
  — Kotlin method-reference form on a Java-shape getter
  (`Account::getId`) works because `getId(): Optional<AccountId>` is a
  declared Kotlin function. `Optional::get` is a Java method reference,
  also valid in Kotlin.
- 2 test methods.

### 13. `AccountTest.kt`

- **Top-level type kind:** plain `class`; no modifier.
- 4 test methods (`calculatesBalance`, `withdrawalSucceeds`,
  `withdrawalFailure`, `depositSuccess`).
- All bodies preserved 1:1; the only syntactic changes are
  - `new AccountId(1L)` → `Account.AccountId(1L)` (the nested data-class
    invocation; Kotlin requires the outer type qualifier when `AccountId`
    is referenced from outside `Account`'s body).
  - `new ActivityWindow(activity1, activity2)` → `ActivityWindow(activity1, activity2)`.
  - `account.getActivityWindow().getActivities()` →
    `account.activityWindow.getActivities()` (property access for
    `activityWindow`; `getActivities()` is a Kotlin `fun`, not a
    synthetic-property-bridged Java getter — same constraint Sprint 6
    flagged).
  - `account.calculateBalance()` — preserved.
  - Static imports continue working via the `object`-based
    `defaultAccount()` / `defaultActivity()` calls.

### 14. `ActivityWindowTest.kt`

- **Top-level type kind:** plain `class`; no modifier.
- 3 test methods (`calculatesStartTimestamp`, `calculatesEndTimestamp`,
  `calculatesBalance`).
- `Assertions.assertThat(...)` → keep the `Assertions.` qualifier (matches
  Java source's `import org.assertj.core.api.Assertions`); alternatively
  switch to `assertThat(...)` via star or wildcard import. **Decision
  (binding):** preserve the `Assertions.assertThat(...)` qualifier to
  minimize diff and signal "syntax-only conversion".
- `new AccountId(1L)` → `Account.AccountId(1L)`.
- `defaultActivity().withTimestamp(...).build()` chain preserved.

## Acceptance checks

- [ ] `find src/test/java -name '*.java'` → **0 matches** (the Sprint 8 milestone).
- [ ] `find src/test/kotlin -name '*.kt' | wc -l` → **exactly 14**.
- [ ] All 14 expected `.kt` paths exist (script-verifiable from the table above).
- [ ] `grep -R "import lombok" src/test/kotlin` → **0 matches**.
- [ ] `grep -R "import io.mockk" src/test/kotlin` → **0 matches** (Mockito stays).
- [ ] `grep -R "io.reflectoring.reviewapp" src/test/kotlin` → **at least 4 matches**
  (3 in `ArchitectureElement.kt` `denyDependency` body — `domain..`,
  `application..` strings; 3 in `DependencyRuleTests.kt`
  `testPackageDependencies` — `domain..`, `application..`, `..` strings;
  conservatively ≥ 4 to allow for formatting variation. Exact expected
  count is 6: 2 in `ArchitectureElement.kt` (the `domain..` and
  `application..` literals in `denyDependency`), 3 in
  `DependencyRuleTests.kt` (`domain..`, `application..`, `..`), and the
  6th is the imported package — but the grep is keyed on
  `"io.reflectoring.reviewapp"` substring presence).
- [ ] `grep -R '@Sql("SendMoneySystemTest.sql")' src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt`
  → 1 match (verbatim, including the relative resource name).
- [ ] `grep -R '@Sql("AccountPersistenceAdapterTest.sql")' src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt`
  → 1 match.
- [ ] `grep -R "import org.junit.jupiter.api.Test" src/test/kotlin` → **≥ 8 matches**
  (one per test class: `BuckPalApplicationTests`, `DependencyRuleTests`,
  `SendMoneySystemTest`, `SendMoneyControllerTest`,
  `AccountPersistenceAdapterTest`, `SendMoneyServiceTest`, `AccountTest`,
  `ActivityWindowTest`; archunit helpers and test-data builders do not
  import `@Test`).
- [ ] `grep -R "import org.junit.Test" src/test/kotlin` → **0 matches** (no JUnit 4).
- [ ] `JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home ./gradlew clean compileKotlin compileTestKotlin test check`
  → BUILD SUCCESSFUL.
- [ ] **Total test count: 16/16 pass** (NOT 44 as the spec mentions — the
  spec's "44 (or more)" was a planner estimate; the actual current count
  is 16, confirmed by Sprint 7 review which independently re-ran the
  suite). Generator should flag this discrepancy in the handoff so the
  evaluator does not treat the lower count as a regression. Per-suite
  breakdown that MUST hold:
  - `BuckPalApplicationTests` → 1/1 PASS.
  - `SendMoneySystemTest` → 1/1 PASS.
  - `DependencyRuleTests` → 2/2 PASS.
  - `AccountTest` → 4/4 PASS.
  - `ActivityWindowTest` → 3/3 PASS.
  - `SendMoneyServiceTest` → 2/2 PASS.
  - `SendMoneyControllerTest` → 1/1 PASS.
  - `AccountPersistenceAdapterTest` → 2/2 PASS.
- [ ] `./gradlew check` → BUILD SUCCESSFUL (ArchUnit rules green; the
  `DependencyRuleTests` and its 4 archunit helper classes must produce
  identical ArchUnit behavior to the Java source).
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"`
  → 2/2 PASS (independent verification that the placeholder
  `io.reflectoring.reviewapp..` strings still drive a no-op check that
  trivially passes — the package doesn't exist on the classpath).
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"`
  → 1/1 PASS (full Spring Boot + H2 + `@Sql` fixture round-trip;
  confirms the resource path resolution still works after the source-set
  move).
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest"`
  → 2/2 PASS (confirms `@DataJpaTest` + `@Import(...::class)` + `@Sql`
  fixture path resolution and `internal` Kotlin visibility from the test
  source set into the persistence module).
- [ ] kotlinc warnings on the 14 new files → **0**
  (`./gradlew clean compileKotlin compileTestKotlin --info | grep -E '^w:|warning:'`
  → 0 matches scoped to `src/test/kotlin`).
- [ ] `grep -R 'TODO\|FIXME\|XXX' src/test/kotlin` → 0 matches.
- [ ] `git status` shows **only**: 14 .java deletions under `src/test/java/...`,
  14 .kt additions under `src/test/kotlin/...`, plus harness meta-files
  (contract, handoff, review). Any other touched path is automatic FAIL.
- [ ] `grep -c "import org.mockito" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`
  → ≥ 2 (Mockito + BDDMockito imports). Confirms Mockito retained.
- [ ] `grep -c "Optional" src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt`
  → ≥ 3 (`Optional.of(id)`, `Optional::get`, `import java.util.Optional`).
  This is the deliberate transitional shim per spec; Sprint 9 will remove.
<!-- EVALUATOR: added checks below -->
- [ ] `grep -c '!!' src/test/kotlin/io/reflectoring/buckpal/archunit/HexagonalArchitecture.kt`
  → **0 matches** (Decision 2: `checkNotNull` only, no `!!`).
- [ ] `grep -F 'checkNotNull' src/test/kotlin/io/reflectoring/buckpal/archunit/HexagonalArchitecture.kt | wc -l`
  → ≥ 3 (one per nullable field exercised by `check()`: `adapters`,
  `applicationLayer`, `configurationPackage`).
- [ ] `grep -F 'io.reflectoring.reviewapp.domain..' src/test/kotlin/io/reflectoring/buckpal/archunit/ArchitectureElement.kt`
  → 1 match (placeholder preserved verbatim in `denyDependency` body).
- [ ] `grep -F 'io.reflectoring.reviewapp.application..' src/test/kotlin/io/reflectoring/buckpal/archunit/ArchitectureElement.kt`
  → 1 match (placeholder preserved verbatim in `denyDependency` body).
- [ ] `grep -F 'io.reflectoring.reviewapp.domain..' src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt`
  → 1 match.
- [ ] `grep -F 'io.reflectoring.reviewapp.application..' src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt`
  → 1 match.
- [ ] `grep -F 'io.reflectoring.reviewapp..' src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt`
  → 1 match (the importer's `.importPackages(...)` arg).
- [ ] `grep -F 'apply { ' src/test/kotlin/io/reflectoring/buckpal/common/AccountTestData.kt | wc -l`
  → ≥ 3 (one per `withX(...) = apply { ... }` chainable setter; verifies
  Decision 1 was actually applied).
- [ ] `grep -F 'apply { ' src/test/kotlin/io/reflectoring/buckpal/common/ActivityTestData.kt | wc -l`
  → ≥ 6 (one per `withX(...) = apply { ... }` chainable setter).
- [ ] `grep -RFn 'import io.mockk' src/test/kotlin` → 0 matches (MockK
  absence, paired with the Mockito import check above — covers rubric
  Behavioral Correctness's "no test framework swap").
- [ ] `git diff --stat HEAD` shows only paths under `src/test/java/...`
  (14 deletions) and `src/test/kotlin/...` (14 additions) plus harness
  meta-files — verifies `src/main/kotlin` was not touched (the
  `Optional<AccountId>` shim, `Account.kt` open/final modifiers, and the
  Mockito-friendly `open` declarations all stay).

## Rubric coverage (per `criteria/kotlin-conversion.md`)

| Rubric criterion | How this contract verifies it |
|------------------|-------------------------------|
| Behavioral Correctness (35%, floor 9) | `./gradlew test check` green; 16/16 pass; per-suite counts pinned; targeted `DependencyRuleTests` (placeholder strings still drive a no-op ArchUnit check that passes); `SendMoneySystemTest` (full HTTP+H2 path); `AccountPersistenceAdapterTest` (JPA + `@Sql`). |
| Idiomatic Kotlin (30%, floor 7) | Test data builders as `object` + fluent builder class with `apply { ... }`; `lateinit var` only for `@Autowired`/`@MockBean` test injection (Spring Kotlin idiom; out of `src/main` anti-pattern grep scope); no MockK introduced; `@WebMvcTest(controllers = [SendMoneyController::class])` Kotlin array literal; `KClass<*>` literals in `@Import`. Optional<> remains only in `SendMoneyServiceTest` per the spec's deliberate transitional shim — flagged. |
| Architectural Integrity (20%, floor 9) | ArchUnit `DependencyRuleTests` 2/2 green; package paths preserved 1:1 from `src/test/java/...` → `src/test/kotlin/...`; archunit helper visibilities preserved (Adapters/ApplicationLayer ctor `internal`); `io.reflectoring.reviewapp` placeholders preserved verbatim. |
| Code Quality (15%, floor 7) | 0 kotlinc warnings on the 14 new files; 0 `TODO`/`FIXME`/`XXX`; file ↔ class name match for all 14; `@Sql` paths preserved verbatim; imports sorted, no `import *`. |

## Idiomatic Kotlin commitments

1. **`object` for stateless top-level helpers** (`AccountTestData`,
   `ActivityTestData`) — Kotlin equivalent of Java's
   `public final class … { public static … }` pattern. `@JvmStatic` on
   `defaultAccount()` / `defaultActivity()` keeps the static-import shape
   defensive (no consumer needs it post-Sprint-8, but matches the original
   JVM symbol).
2. **Chainable builder via `apply { … }`** — `withX(...) = apply { this.x = x }`
   on each builder method is the canonical Kotlin one-line fluent setter.
   Preserves the Java `@Builder`-style call sites 1:1 without forcing every
   test body to switch to named-arg constructors.
3. **`lateinit var` for `@Autowired` / `@MockBean` test fields** —
   documented Spring Kotlin idiom for tests. Out of scope of the production
   `src/main/kotlin` anti-pattern grep. Flagged in handoff.
4. **`KClass<*>` literals in annotations** — `@WebMvcTest(controllers = [SendMoneyController::class])`,
   `@Import(AccountPersistenceAdapter::class, AccountMapper::class)`,
   `@ExtendWith(SpringExtension::class)` — Kotlin class-literal syntax over
   `.class`.
5. **Top-level `@Test fun ...()` Kotlin style** — `fun testSendMoney()`
   instead of `void testSendMoney() throws Exception`. No throws clause
   (Kotlin doesn't have checked exceptions); `Exception` propagation is
   automatic.
6. **Preserve Mockito's BDD DSL verbatim** — `given(...).willReturn(...)`,
   `then(...).should()...`, `eq(...)`. No `when(...)` form is used so the
   Kotlin keyword collision is sidestepped naturally. (Documented in
   Risks for completeness.)

## Risks specific to this sprint

1. **`Mockito.when()` keyword collision (NOT triggered, listed for
   completeness).** The Java source uses BDD form exclusively
   (`given(...).willReturn(...)`); zero `when(...).thenReturn(...)` calls.
   So no `` Mockito.`when`(...) `` backtick escapes are required in the
   Kotlin source. **If a future review requests a swap to classical
   `when(...).thenReturn(...)`, the Kotlin form is `` Mockito.`when`(stub).thenReturn(value) ``** —
   but this sprint does not introduce it.
2. **`@Autowired` field injection on test classes.** Spring Boot tests
   under `@SpringBootTest`/`@WebMvcTest`/`@DataJpaTest` traditionally use
   field injection. Kotlin requires `lateinit var` for `@Autowired`
   non-null reference fields. The rubric's anti-pattern grep for
   `lateinit var` is scoped to `src/main/kotlin` (per the criteria
   document, line 102: `grep -Rn "lateinit var" src/main/kotlin`). Test
   code is out of that scope. **Generator: surface this explicitly in
   the handoff so the Evaluator does not flag it on Idiomatic Kotlin.**
3. **`AccountId`/`ActivityId` nested-type references from tests.** Java's
   `import io.reflectoring.buckpal.account.domain.Account.AccountId;` works
   in Kotlin too (`import io.reflectoring.buckpal.account.domain.Account.AccountId`),
   so `AccountId(1L)` continues compiling without the `Account.` prefix.
   Tests already use this import. **Verification:**
   `SendMoneySystemTest`, `SendMoneyControllerTest`,
   `AccountPersistenceAdapterTest`, `SendMoneyServiceTest`, `AccountTest`,
   `ActivityWindowTest`, `AccountTestData`, `ActivityTestData` all do this
   import in the Java source.
4. **`Account.getId(): Optional<AccountId>` stub in `SendMoneyServiceTest`.**
   Per spec: keep the Optional form for now; Sprint 9 collapses the shim.
   The Generator flags in the handoff that `Optional` and
   `import java.util.Optional` survive only in this one test file as a
   deliberate transitional choice.
5. **JUnit version: JUnit 5 (Jupiter).** Spring Boot 2.4.3 + the
   `org.junit.jupiter` import in every Java test confirms JUnit 5. The
   Kotlin conversion stays on Jupiter; `org.junit.jupiter.api.Test` is the
   only `@Test` annotation; `org.junit.jupiter.api.extension.ExtendWith`
   for `BuckPalApplicationTests`'s `@ExtendWith(SpringExtension::class)`.
6. **`internal` visibility crossing source sets (`compileKotlin` → test).**
   Sprint 6 made `AccountPersistenceAdapter`, `AccountMapper`, and
   `ActivityRepository` `internal`. Kotlin's `internal` modifier is
   *module-scoped*; the Gradle `test` source set shares the same module
   as `main`, so `internal` types are directly visible from
   `compileTestKotlin`. Sprint 6's review verified this works for Java
   tests (Java's bytecode-public view of `internal` Kotlin classes);
   Kotlin-to-Kotlin should work even more cleanly. **Verification check:**
   `AccountPersistenceAdapterTest.kt` compiles without
   `@Suppress("INVISIBLE_REFERENCE")`.
7. **`@WebMvcTest(controllers = SendMoneyController.class)` → Kotlin
   array literal.** The Java annotation attribute is `Class<?>[]`; Kotlin
   compiles `[SendMoneyController::class]` to a single-element class
   array. With one entry, the brackets are technically optional in
   Kotlin (`controllers = SendMoneyController::class`), but bracket form
   is more idiomatic and unambiguous — **decision: use brackets**.
8. **Mockito mock of `Account` (a Kotlin `open class`).** The Sprint 2
   `Account` is declared `open` precisely so Mockito CGLIB can subclass.
   `getId()`, `withdraw`, `deposit`, `calculateBalance` are also `open`.
   No additional production-code change required.
9. **`getActivities()` is a Kotlin `fun`, NOT a synthetic property.**
   `ActivityWindow.kt:53` declares `fun getActivities(): List<Activity>`.
   Test code calling `account.getActivityWindow().getActivities()` becomes
   `account.activityWindow.getActivities()` in Kotlin — the
   `getActivityWindow()` Java accessor maps to the Kotlin property
   `activityWindow`, but `getActivities()` stays a function call (Sprint 6
   review surfaced this same point for `AccountPersistenceAdapter`).
10. **Test count is 16, not 44.** The spec's "44 (or more)" was a planner
    estimate; the actual current pass count is 16 (8 suites). Sprint 7
    review confirms this. Generator: explicitly state in the handoff that
    16/16 is the correct end state and that this contract pins per-suite
    counts to lock the surface.

## Out of scope

- Removing `Account.kt`'s `Optional<AccountId>` shim — Sprint 9. The
  `SendMoneyServiceTest` Mockito stubs explicitly drive the Optional form
  in this sprint; collapsing the shim cascades to that test and is
  deferred.
- Removing Lombok dependency from `build.gradle` — Sprint 9. After this
  sprint, Lombok has zero consumers, but the dependency line is still
  present.
- Removing `apply plugin: 'java'` / `'java-library'` from `build.gradle` —
  Sprint 9.
- Switching `build.gradle` → `build.gradle.kts` — Sprint 9 optional.
- Introducing MockK or `mockito-kotlin` — out of scope per spec.
- Converting `lateinit var` `@Autowired` test fields to constructor
  injection via `@TestConstructor` — non-trivial Spring + JUnit 5 surface
  change, not requested by spec, would be gratuitous.
- Any change to test resource files (`.sql` fixtures, `application.yml`,
  `application-test.yml` if present).
- Renaming any test method, changing any assertion, or weakening any
  `should()` / `assertThat(...)` call.

## Implementation order

Dependency-driven (consumers wait for their producers):

1. **archunit helpers** (consumed by `DependencyRuleTests`):
   1. `ArchitectureElement.kt` — base class, no in-scope deps.
   2. `Adapters.kt` — depends on `ArchitectureElement`.
   3. `ApplicationLayer.kt` — depends on `ArchitectureElement`.
   4. `HexagonalArchitecture.kt` — depends on `ArchitectureElement`,
      `Adapters`, `ApplicationLayer`.
2. **test data builders** (consumed by service/controller/persistence/domain tests):
   1. `ActivityTestData.kt` — depends on Sprint 2's `Activity`/`Money`.
   2. `AccountTestData.kt` — depends on Sprint 2's `Account`/`Money`/`ActivityWindow`
      **and** on `ActivityTestData` (the `defaultAccount()` seed calls
      `ActivityTestData.defaultActivity()` twice).
3. **Individual unit/component tests** (each independent, can be parallel-converted):
   1. `AccountTest.kt`.
   2. `ActivityWindowTest.kt`.
   3. `SendMoneyServiceTest.kt`.
   4. `SendMoneyControllerTest.kt`.
   5. `AccountPersistenceAdapterTest.kt`.
4. **Integration / context-load tests** (depend on root Spring config):
   1. `BuckPalApplicationTests.kt`.
   2. `SendMoneySystemTest.kt`.
5. **ArchUnit driver:**
   1. `DependencyRuleTests.kt` — depends on the 4 archunit helpers.

After all 14 `.kt` files exist:

6. Run `./gradlew compileTestKotlin` — must succeed before any deletion.
7. Delete the 14 `.java` files (`git rm`).
8. Run `./gradlew compileTestKotlin compileTestJava` — `compileTestJava`
   should report `NO-SOURCE` (no `.java` left in `src/test/java`).
9. Run `./gradlew test` — 16/16 must pass.
10. Targeted runs (one per suite, in failure-order: simplest first):
    - `AccountTest`, `ActivityWindowTest`,
    - `SendMoneyServiceTest`,
    - `SendMoneyControllerTest`,
    - `AccountPersistenceAdapterTest`,
    - `BuckPalApplicationTests`,
    - `SendMoneySystemTest`,
    - `DependencyRuleTests`.
11. `./gradlew check` → BUILD SUCCESSFUL.
12. `./gradlew clean compileKotlin compileTestKotlin --info | grep -E '^w:|warning:'`
    → 0 matches scoped to `src/test/kotlin`.
13. `find src/test/java -name '*.java'` → empty (THE Sprint 8 milestone).
14. Write `.claude/harness/workspace/handoffs/sprint-08-handoff.md`.
15. **DO NOT commit** — orchestrator handles the commit after Phase B PASS.

## Self-check (Generator, before writing handoff)

- [ ] All 14 `.kt` files compile.
- [ ] All 14 `.java` files deleted (`git rm`).
- [ ] `find src/test/java -name '*.java'` → 0 (THE Sprint 8 milestone).
- [ ] `find src/test/kotlin -name '*.kt' | wc -l` → 14.
- [ ] All 14 expected paths exist (per Files-in-scope table).
- [ ] `./gradlew clean compileKotlin compileTestKotlin test check` → BUILD SUCCESSFUL, 16/16 pass.
- [ ] Per-suite counts match: BuckPalApplicationTests 1/1, SendMoneySystemTest 1/1, DependencyRuleTests 2/2, AccountTest 4/4, ActivityWindowTest 3/3, SendMoneyServiceTest 2/2, SendMoneyControllerTest 1/1, AccountPersistenceAdapterTest 2/2.
- [ ] No `import lombok` in `src/test/kotlin`.
- [ ] No `import io.mockk` in `src/test/kotlin`.
- [ ] `io.reflectoring.reviewapp` strings preserved verbatim in both
  `ArchitectureElement.kt` and `DependencyRuleTests.kt`.
- [ ] `@Sql("SendMoneySystemTest.sql")` and `@Sql("AccountPersistenceAdapterTest.sql")`
  preserved verbatim.
- [ ] 0 kotlinc warnings on the 14 new files.
- [ ] `Optional` surface preserved only in `SendMoneyServiceTest.kt`
  (deliberate transitional shim; Sprint 9 will remove).
- [ ] `git status` — only in-scope changes (14 java deletions + 14 kt
  additions + harness meta-files; nothing else).
- [ ] No test signature, name, or assertion modified — verified by
  diff-comparing each test method's body shape against the Java source
  (control-flow and call shapes preserved 1:1).
