# Product Spec — BuckPal Java → Kotlin Migration

**Author:** Planner agent
**Date:** 2026-05-17
**Codebase:** `/Users/hannamil/Workspace/clean-architecture` (branch `kotlin-migration`)
**Source:** *Get Your Hands Dirty on Clean Architecture* companion code
(Wikibook 한국어 판). 29 production `.java` files, 14 test `.java` files,
Spring Boot 2.4.3, Java 11, Lombok, ArchUnit, H2.

---

## 1. Migration goal

Convert **every** production and test source from Java to Kotlin while
preserving:

- All observable behavior — every existing JUnit test green, every ArchUnit
  rule green, `POST /accounts/send/{src}/{dst}/{amount}` still working
  against H2 with the `SendMoneySystemTest.sql` fixture.
- The hexagonal package layout under `io.reflectoring.buckpal.**`. Package
  paths, class names, and `@WebAdapter` / `@PersistenceAdapter` / `@UseCase`
  semantics are load-bearing for the ArchUnit rules and for the book's
  pedagogical value.
- The public bean topology of the Spring Boot application (same `@Component`
  / `@Configuration` graph; same `application.yml` keys).

Migration succeeds when (a) every `.java` file under `src/` has been replaced
by its Kotlin counterpart in `src/main/kotlin/` or `src/test/kotlin/`,
(b) Lombok is removed from `build.gradle`, and (c) `./gradlew build` is
green from a clean checkout.

## 2. Non-negotiable invariants

These hold at **every** sprint boundary, not just at the end:

1. Public package paths under `io.reflectoring.buckpal.**` remain stable.
   Nested types stay nested (`Account.AccountId`, `Activity.ActivityId`).
2. All existing JUnit + ArchUnit tests pass without weakening their
   assertions. **Modifying a test to make it green is FAIL.**
3. The Spring Boot app boots; `POST /accounts/send/{src}/{dst}/{amount}`
   returns the same HTTP behavior end-to-end (`SendMoneySystemTest` is the
   reference).
4. After Sprint 9, no `import lombok.*` anywhere; no `.java` file under
   `src/`.
5. `transferThreshold` (`application.yml`) → `BuckPalConfigurationProperties.transferThreshold`
   wiring continues to work via `@ConfigurationProperties(prefix = "buckpal")`.

## 3. Target Kotlin conventions

The Generator should reach for these idioms where they apply naturally. Do
**not** force them in places they don't fit.

- `data class` for value-bearing types: `Money`, `AccountId`, `ActivityId`,
  `Activity`, `SendMoneyCommand`, JPA DTO entities, etc.
- `val` over `var`. Mutability only where the original code required it
  (e.g., `ActivityWindow.activities` mutable list).
- Primary-constructor injection for Spring components. No `@Autowired field`.
- `companion object` for static-like factories where the call sites benefit
  (`Money.ZERO`, `Money.of`, `Account.withId`, `Account.withoutId`). Prefer
  top-level constants for simple cases.
- `operator fun plus/minus/compareTo` on `Money` so the domain code
  naturally reads `a + b` instead of `Money.add(a, b)`.
- Nullable types (`T?`) instead of `Optional<T>` at port and domain
  boundaries that the codebase owns. `Account.getId()` currently returns
  `Optional<AccountId>`; in Kotlin it becomes a nullable property
  `val id: AccountId?` with downstream call sites adjusted.
- Replace Lombok one-for-one:
  - `@Value` / `@Data` on immutable types → `data class` with `val` props.
  - `@AllArgsConstructor` / `@NoArgsConstructor` → primary constructor with
    defaults, plus the `kotlin-jpa` plugin to synthesize no-arg ctors for
    JPA entities.
  - `@RequiredArgsConstructor` → primary constructor with `val` params.
  - `@Slf4j` → `private val log = LoggerFactory.getLogger(<Class>::class.java)`
    on a companion object, only where actually used.
  - `@Builder` on `SendMoneyCommand` is **not present** — only
    `MoneyTransferProperties` and JPA entities use combined ctors; those
    map cleanly to default args.
- `JvmInline value class` is **not** appropriate for `AccountId`/`ActivityId`
  here because they are nested inside `Account`/`Activity`. Use `data class`
  instead.
- `internal` for package-private Java classes (`SendMoneyController`,
  `GetAccountBalanceService`, `NoOpAccountLock`, persistence adapter types).
  Do not widen to `public`.
- `kotlin-spring` plugin makes `open` automatic on Spring-annotated classes
  — do not hand-add `open` keywords.

## 4. Sprint plan

Ten sprints. Each sprint is one commit, leaves the repo green, and is
independent enough to revert in isolation. Sprint numbers are zero-padded
to two digits in workspace filenames (`sprint-00-contract.md`, …).

### Sprint 0 — Build configuration

**Goal:** Introduce Kotlin tooling so subsequent sprints can land `.kt`
files. Keep Java compilation working side-by-side.

**Files in scope:**
- `build.gradle`
- `gradle/` (only if a wrapper version bump is needed; otherwise untouched)

**Concrete tasks:**
- Add Kotlin Gradle plugin (`org.jetbrains.kotlin.jvm`) at a version
  compatible with Spring Boot 2.4.3 and Kotlin 1.6.x or 1.7.x. Choose the
  lowest version that works to minimize fallout.
- Add `org.jetbrains.kotlin.plugin.spring` and `org.jetbrains.kotlin.plugin.jpa`.
- Add deps: `org.jetbrains.kotlin:kotlin-stdlib-jdk8`,
  `org.jetbrains.kotlin:kotlin-reflect`,
  `com.fasterxml.jackson.module:jackson-module-kotlin`.
- Test deps: `org.jetbrains.kotlin:kotlin-test`,
  `org.jetbrains.kotlin:kotlin-test-junit5`,
  `io.mockk:mockk` (optional — Mockito works fine for now, do not introduce
  unless a test sprint demands it).
- `compileKotlin { kotlinOptions { jvmTarget = '11' } }` and same for tests.
- Source sets: ensure `src/main/kotlin` and `src/test/kotlin` are picked up
  (default with the Kotlin plugin, but verify by creating empty dirs).

**Hard exit criteria:**
- `[ ] ./gradlew clean compileKotlin compileTestKotlin → BUILD SUCCESSFUL`
  (with zero .kt sources — compileKotlin should no-op).
- `[ ] ./gradlew test → all existing 44 tests still pass`
- `[ ] git diff src/main/java src/test/java → empty`
- `[ ] grep -E "kotlin" build.gradle → at least 3 matches (plugin + stdlib + jvmTarget)`

**Out of scope:** Any `.java` → `.kt` conversion. Removing Lombok. Switching
to `build.gradle.kts`.

---

### Sprint 1 — `common/` package

**Goal:** Convert the 4-class `common` package — pure-annotation and tiny
abstract class — establishing the basic conversion pattern.

**Files in scope (4 → 4):**
- `src/main/java/io/reflectoring/buckpal/common/UseCase.java`
- `src/main/java/io/reflectoring/buckpal/common/WebAdapter.java`
- `src/main/java/io/reflectoring/buckpal/common/PersistenceAdapter.java`
- `src/main/java/io/reflectoring/buckpal/common/SelfValidating.java`

**Conversion notes:**
- Spring's `@AliasFor(annotation = Component.class)` is supported on Kotlin
  meta-annotations — keep `value()` aliasing.
- `SelfValidating<T>`: keep the unchecked cast `this as T` with a
  `@Suppress("UNCHECKED_CAST")` annotation, mirroring the original.

**Hard exit criteria:**
- `[ ] find src/main/java/io/reflectoring/buckpal/common -name '*.java' → empty`
- `[ ] find src/main/kotlin/io/reflectoring/buckpal/common -name '*.kt' → 4 files`
- `[ ] ./gradlew test → BUILD SUCCESSFUL`
- `[ ] ArchUnit DependencyRuleTests → PASS` (annotation FQNs unchanged)

**Out of scope:** Any other package.

---

### Sprint 2 — `account/domain/`

**Goal:** Convert the domain core (5 source files containing 7 types).

**Files in scope (5 .java → 5+ .kt):**
- `Money.java`
- `Account.java` (also contains nested `AccountId`)
- `Activity.java` (also contains nested `ActivityId`)
- `ActivityWindow.java`

**Conversion notes:**
- `Money`: `data class Money(val amount: BigInteger)`. Add `companion object {
  val ZERO; fun of(value: Long); fun add(a, b); fun subtract(a, b) }`
  for source-compat with existing callers. Also add
  `operator fun plus`, `operator fun minus`, `operator fun unaryMinus`,
  `operator fun compareTo` so future sprints can simplify call sites.
  Keep the boolean helpers (`isPositiveOrZero`, `isNegative`, `isPositive`,
  `isGreaterThanOrEqualTo`, `isGreaterThan`).
  **Critical:** preserve `Money.add` and `Money.subtract` static-style
  factories — they are called from `Account`, `ActivityWindow`,
  `AccountMapper`. Removing them would cascade.
- `Account`: primary constructor is `private`; expose `Account.withId(...)`
  and `Account.withoutId(...)` via `companion object`. `id` becomes
  `val id: AccountId?`; `getId()` returning `Optional<AccountId>` is the
  awkward case — in Kotlin we keep a public `val id: AccountId?` and adjust
  callers in Sprint 4 / Sprint 6. Until then, **preserve** `getId():
  Optional<AccountId>` as an explicit Kotlin function so Sprint-2 callers
  still compile.
- `Account.AccountId` is a nested `data class` with `val value: Long`. Stays
  nested.
- `Activity`: `data class` with `val id: ActivityId?` and `val ownerAccountId
  / sourceAccountId / targetAccountId: AccountId` non-null, `val timestamp:
  LocalDateTime`, `val money: Money`. Replace the secondary constructor
  (the 5-arg one without id) with a default value `id: ActivityId? = null`.
- `Activity.ActivityId` is a nested `data class` with `val value: Long`.
- `ActivityWindow`: `class ActivityWindow` with primary `(activities:
  MutableList<Activity>)` plus secondary `vararg constructor(vararg
  activities: Activity)`. `getActivities()` returns `List<Activity>` (read-
  only view). `addActivity` stays mutating.

**Hard exit criteria:**
- `[ ] find src/main/java/io/reflectoring/buckpal/account/domain -name '*.java' → empty`
- `[ ] ./gradlew test --tests "io.reflectoring.buckpal.account.domain.*" → all pass`
- `[ ] ./gradlew test → all pass (no test regressed in dependent layers)`
- `[ ] ArchUnit DependencyRuleTests → PASS`
- `[ ] grep -R "import lombok" src/main/kotlin/io/reflectoring/buckpal/account/domain → empty`

**Out of scope:** Removing `Money.add`/`subtract` static-style factories
(future sprint after all callers are Kotlin and can use operators).

---

### Sprint 3 — `account/application/port/`

**Goal:** Convert the 6 port files (3 in, 3 out).

**Files in scope (6):**
- `port/in/SendMoneyUseCase.java`, `SendMoneyCommand.java`,
  `GetAccountBalanceQuery.java`
- `port/out/LoadAccountPort.java`, `UpdateAccountStatePort.java`,
  `AccountLock.java`

**Conversion notes:**
- Interfaces convert 1:1.
- `SendMoneyCommand`: `data class SendMoneyCommand(val sourceAccountId:
  AccountId, val targetAccountId: AccountId, val money: Money) :
  SelfValidating<SendMoneyCommand>()` — call `validateSelf()` from an
  `init` block. Annotate fields with `@field:NotNull` so Bean Validation
  sees the annotations on the underlying field, not the constructor
  parameter or property getter.
- Use `javax.validation.constraints.NotNull` (Spring Boot 2.4.3, still
  Jakarta-pre-namespace).

**Hard exit criteria:**
- `[ ] find src/main/java/io/reflectoring/buckpal/account/application/port -name '*.java' → empty`
- `[ ] ./gradlew test → all pass`
- `[ ] ./gradlew test --tests "io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest" → pass`
  (this exercises `SendMoneyCommand` heavily).

**Out of scope:** `Optional<AccountId>` removal from `Account` — Sprint 2
leaves `getId()` returning `Optional`; this sprint may *call* it from the
ports, but should not delete it yet.

---

### Sprint 4 — `account/application/service/`

**Goal:** Convert the 5 service classes.

**Files in scope (5):**
- `SendMoneyService.java`
- `GetAccountBalanceService.java`
- `MoneyTransferProperties.java`
- `NoOpAccountLock.java`
- `ThresholdExceededException.java`

**Conversion notes:**
- `SendMoneyService`: primary constructor injection. `@UseCase` and
  `@Transactional` carry over to the Kotlin class. The body translates 1:1.
  This is the right sprint to **flip `Optional<AccountId>` to nullable** in
  the call sites, since `Account` is already in Kotlin: use
  `?: error("expected source account ID not to be empty")` and update
  `Account.getId(): Optional<AccountId>` shim in Sprint 2 to return
  `AccountId?` (drop `Optional` from `Account` and `getId()` shim). If that
  cascades wider than intended, defer the Optional removal to Sprint 8
  (tests) — but flag clearly in the handoff.
- `MoneyTransferProperties`: `data class MoneyTransferProperties(var
  maximumTransferThreshold: Money = Money.of(1_000_000L))`. `var` because
  the JavaBeans-style setter is what Spring uses for
  `@ConfigurationProperties` binding semantics in `BuckPalConfiguration`.
  (Actually wired by ctor in `BuckPalConfiguration`, so `val` is also fine
  — Generator should choose and justify in handoff.)
- `NoOpAccountLock`, `ThresholdExceededException`, `GetAccountBalanceService`
  are straightforward.

**Hard exit criteria:**
- `[ ] find src/main/java/io/reflectoring/buckpal/account/application/service -name '*.java' → empty`
- `[ ] ./gradlew test → all pass`
- `[ ] grep -R "Optional<" src/main/kotlin/io/reflectoring/buckpal/account → empty OR justified in handoff`

**Out of scope:** Web/persistence adapters.

---

### Sprint 5 — `account/adapter/in/web/`

**Goal:** Convert the single web adapter.

**Files in scope (1):**
- `SendMoneyController.java`

**Conversion notes:**
- `internal class SendMoneyController(private val sendMoneyUseCase:
  SendMoneyUseCase)`. Annotate with `@WebAdapter` and `@RestController`.
- `@PostMapping` path unchanged: `/accounts/send/{sourceAccountId}/{targetAccountId}/{amount}`.
- `@PathVariable("...")` keeps explicit names — do not rely on Kotlin
  parameter-name reflection.

**Hard exit criteria:**
- `[ ] find src/main/java/io/reflectoring/buckpal/account/adapter/in -name '*.java' → empty`
- `[ ] ./gradlew test --tests "io.reflectoring.buckpal.account.adapter.in.web.SendMoneyControllerTest" → pass`
- `[ ] ./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest" → pass`

**Out of scope:** Persistence.

---

### Sprint 6 — `account/adapter/out/persistence/`

**Goal:** Convert all 6 persistence files. Highest-risk sprint due to JPA
+ Kotlin interactions.

**Files in scope (6):**
- `AccountJpaEntity.java`
- `ActivityJpaEntity.java`
- `AccountMapper.java`
- `SpringDataAccountRepository.java`
- `ActivityRepository.java`
- `AccountPersistenceAdapter.java`

**Conversion notes:**
- JPA entities (`AccountJpaEntity`, `ActivityJpaEntity`): use **`class`**,
  not `data class`. JPA requires mutable properties (for lazy loading and
  proxy generation) and a no-arg constructor. `kotlin-jpa` plugin
  synthesizes the no-arg ctor; declare all properties as `var` with default
  values:
  ```
  @Entity @Table(name="activity") internal class ActivityJpaEntity(
      @Id @GeneratedValue var id: Long? = null,
      @Column var timestamp: LocalDateTime? = null,
      @Column var ownerAccountId: Long? = null,
      ...
  )
  ```
  All columns become nullable to retain `@NoArgsConstructor` semantics.
- `AccountMapper`: stateless class. Convert each method 1:1. The `id == null`
  ternary in `mapToJpaEntity` becomes a safe-call `activity.id?.value`.
- `AccountPersistenceAdapter`: `internal class` with primary ctor injection.
  The `orZero` helper becomes a small private fun or an `?: 0L` Elvis on
  the call site — Generator's choice.
- ActivityRepository / SpringDataAccountRepository: convert to Kotlin
  `interface ... : JpaRepository<>`. Annotate with `@Query` for the JPQL
  methods. JPQL strings are preserved verbatim — they reference the
  **Kotlin entity class simple name**, which must remain `ActivityJpaEntity`.

**Hard exit criteria:**
- `[ ] find src/main/java/io/reflectoring/buckpal/account/adapter/out -name '*.java' → empty`
- `[ ] ./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest" → pass`
- `[ ] ./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest" → pass` (the @Sql fixture loads correctly).
- `[ ] ./gradlew test → all pass`

**Out of scope:** Root Spring config.

---

### Sprint 7 — Root Spring Boot setup

**Goal:** Convert the 3 root-level classes.

**Files in scope (3):**
- `BuckPalApplication.java`
- `BuckPalConfiguration.java`
- `BuckPalConfigurationProperties.java`

**Conversion notes:**
- `BuckPalApplication`: top-level `fun main(args: Array<String>) { runApplication<BuckPalApplication>(*args) }`.
  The class itself becomes `@SpringBootApplication class BuckPalApplication`.
- `BuckPalConfiguration`: `@Configuration` class with the `moneyTransferProperties`
  `@Bean` method.
- `BuckPalConfigurationProperties`: data class with `var transferThreshold:
  Long = Long.MAX_VALUE`. `@ConstructorBinding` is **not** added — Spring
  Boot 2.4.3 supports JavaBeans-style binding which works for Kotlin `var`
  properties.

**Hard exit criteria:**
- `[ ] find src/main/java -name '*.java' → empty`
- `[ ] ./gradlew bootRun & sleep 12; curl -i -X POST 'http://localhost:8080/accounts/send/1/2/500' ; kill %1`
  → HTTP 200 (or whatever the system test expects).
- `[ ] ./gradlew test → all pass`

**Out of scope:** Test sources.

---

### Sprint 8 — Test sources

**Goal:** Convert all 14 test source files to Kotlin.

**Files in scope (14):**
- `src/test/java/io/reflectoring/buckpal/BuckPalApplicationTests.java`
- `src/test/java/io/reflectoring/buckpal/DependencyRuleTests.java`
- `src/test/java/io/reflectoring/buckpal/SendMoneySystemTest.java`
- `src/test/java/io/reflectoring/buckpal/common/AccountTestData.java`
- `src/test/java/io/reflectoring/buckpal/common/ActivityTestData.java`
- `src/test/java/io/reflectoring/buckpal/archunit/Adapters.java`
- `src/test/java/io/reflectoring/buckpal/archunit/ApplicationLayer.java`
- `src/test/java/io/reflectoring/buckpal/archunit/ArchitectureElement.java`
- `src/test/java/io/reflectoring/buckpal/archunit/HexagonalArchitecture.java`
- `src/test/java/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.java`
- `src/test/java/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.java`
- `src/test/java/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.java`
- `src/test/java/io/reflectoring/buckpal/account/domain/AccountTest.java`
- `src/test/java/io/reflectoring/buckpal/account/domain/ActivityWindowTest.java`

**Conversion notes:**
- Test data builders (`AccountTestData`, `ActivityTestData`) — Java
  `@Builder` style → Kotlin functions with default args, *or* DSL-style
  `accountData { … }`. Default args are simpler and require fewer call-site
  changes. Generator picks one; whichever picks, all callers in the same
  sprint adjust.
- ArchUnit helpers (`HexagonalArchitecture`, `Adapters`, `ApplicationLayer`,
  `ArchitectureElement`) — straightforward class conversion. The
  intentional `"io.reflectoring.reviewapp"` strings in
  `ArchitectureElement.denyDependency` and `DependencyRuleTests.testPackageDependencies`
  are **preserved verbatim** — they appear to be intentional placeholders
  from the book; do not "fix" them.
- Mockito tests stay on Mockito (do not migrate to MockK in this sprint).
- `@Sql` resource paths in `SendMoneySystemTest` and
  `AccountPersistenceAdapterTest` reference
  `src/test/resources/...` paths that are unaffected.

**Hard exit criteria:**
- `[ ] find src/test/java -name '*.java' → empty`
- `[ ] ./gradlew test → all 44 (or more) tests pass`
- `[ ] grep -R "import lombok" src/test/kotlin → empty`
- `[ ] ./gradlew check → ArchUnit rules green`

**Out of scope:** Removing Lombok dependency from `build.gradle`.

---

### Sprint 9 — Cleanup & verification

**Goal:** Remove residual Java-isms and verify the migration end-to-end.

**Files in scope:**
- `build.gradle` → consider migrating to `build.gradle.kts`. If a clean
  rewrite would risk regressions, keep `.gradle` Groovy file and just
  edit it; the Evaluator should treat the format choice as low-stakes.
- Any leftover Java files anywhere under `src/`.

**Concrete tasks:**
- Remove `compileOnly 'org.projectlombok:lombok'` and
  `annotationProcessor 'org.projectlombok:lombok'` deps.
- Remove `apply plugin: 'java'` and `apply plugin: 'java-library'` if no
  `.java` remain. **Keep** `compileJava { sourceCompatibility = 11 }` only
  if Gradle still references it through the Kotlin plugin; otherwise drop.
- Remove `delombok` references if any.
- Final smoke:
  - `./gradlew clean build`
  - `./gradlew bootRun &; sleep 12; curl … ; kill %1`
  - `grep -R "import lombok" src → empty`
  - `find src -name '*.java' → empty`
- Update `README.md` *only if* it mentions Lombok or JDK 11 in a way that
  is now wrong. Keep edits minimal.

**Hard exit criteria:**
- `[ ] find src -name '*.java' → empty`
- `[ ] grep -R "lombok" build.gradle build.gradle.kts 2>/dev/null → empty`
- `[ ] grep -R "import lombok" src → empty`
- `[ ] ./gradlew clean build → BUILD SUCCESSFUL`
- `[ ] ./gradlew bootRun smoke → endpoint returns expected response`

**Out of scope:** Any pure stylistic refactors. The migration is over.

---

## 5. Risk register

1. **Lombok `@Value` ↔ Kotlin `data class` equality semantics.** Money's
   `BigInteger` field will use `BigInteger.equals` in both cases (Lombok
   generated `equals` is reflection-on-fields, BigInteger.equals is
   scale-aware for BigDecimal but not BigInteger — safer here than the
   article's example). Still: add a Money equality assertion as a sanity
   probe in Sprint 2's contract.
2. **`Account.getId(): Optional<AccountId>` cascading.** `SendMoneyService`
   calls `.orElseThrow(…)`. Until that line is in Kotlin, `Account` must
   either still expose `Optional<AccountId>` or the Generator must keep a
   shim. Plan: Sprint 2 keeps the `Optional`-returning shim; Sprint 4
   removes it once all callers are Kotlin.
3. **JPA + Kotlin no-arg ctors.** Without `kotlin-jpa` plugin, Hibernate
   will fail to instantiate `AccountJpaEntity`/`ActivityJpaEntity` reflectively.
   Sprint 0 must include this plugin.
4. **Spring + `final` classes.** Kotlin classes are `final` by default;
   Spring needs `open` for CGLIB proxies (`@Transactional`,
   `@Configuration`). `kotlin-spring` plugin handles this — Sprint 0 must
   include it.
5. **ArchUnit reflection over `.java` vs `.kt`.** ArchUnit operates on the
   compiled `.class` files; package layout is what matters. As long as
   sprint 1 doesn't move `common.UseCase` etc. out of their package, ArchUnit
   stays green. **The `io.reflectoring.reviewapp` strings in some ArchUnit
   helpers are intentional and must be preserved verbatim.**
6. **`SendMoneyCommand` extends `SelfValidating<SendMoneyCommand>`.** Kotlin
   `data class` extending a generic parent is allowed. Bean Validation
   needs `@field:NotNull` so the annotation lands on the JVM field, not
   the property getter.
7. **`@Builder` on test data.** None present in production code; only test
   builders simulate it. Sprint 8 handles this.
8. **`MoneyTransferProperties` default value `Money.of(1_000_000L)` vs.
   binding flow.** Currently bound through `BuckPalConfiguration.moneyTransferProperties(...)`
   bean, not directly. Default value is therefore non-binding-relevant —
   keep as `val` with a default; constructor invocation in the `@Bean`
   method passes the actual threshold.

---

## 6. Sprint dependency graph

```
Sprint 0 (build)
   │
   ▼
Sprint 1 (common)
   │
   ▼
Sprint 2 (domain) ───────────┐
   │                          ▼
   │                       Sprint 3 (ports) ── Sprint 5 (web)
   │                          │                     │
   ▼                          ▼                     │
Sprint 4 (services) ◄─────────┘                     │
   │                                                │
   ▼                                                │
Sprint 6 (persistence) ◄────────────────────────────┘
   │
   ▼
Sprint 7 (root)
   │
   ▼
Sprint 8 (tests)
   │
   ▼
Sprint 9 (cleanup)
```

Each sprint's hard exit criteria includes `./gradlew test` green, so every
node is a safe rollback point.

---

## 7. Self-check (Planner)

- [x] Every sprint leaves the repo with `./gradlew test` green.
- [x] No sprint depends on a later sprint's changes (Sprint 4's `Optional`
      removal explicitly noted as deferrable if cascading).
- [x] ArchUnit assumptions preserved by ordering (Sprint 1 keeps annotation
      FQNs; later sprints don't move packages).
- [x] Last sprint includes a full-build + boot smoke test.
- [x] Risk register lists concrete hazards with mitigation per sprint.

**Planner done. Ready for Sprint 0 contract negotiation.**
