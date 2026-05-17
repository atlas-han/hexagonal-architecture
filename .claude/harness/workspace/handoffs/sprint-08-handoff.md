# Sprint 8 Handoff — Test sources migration

## What changed

Test-source conversions (14 .java deleted → 14 .kt added):

- `src/test/java/io/reflectoring/buckpal/BuckPalApplicationTests.java` → **deleted**
- `src/test/java/io/reflectoring/buckpal/DependencyRuleTests.java` → **deleted**
- `src/test/java/io/reflectoring/buckpal/SendMoneySystemTest.java` → **deleted**
- `src/test/java/io/reflectoring/buckpal/common/AccountTestData.java` → **deleted**
- `src/test/java/io/reflectoring/buckpal/common/ActivityTestData.java` → **deleted**
- `src/test/java/io/reflectoring/buckpal/archunit/Adapters.java` → **deleted**
- `src/test/java/io/reflectoring/buckpal/archunit/ApplicationLayer.java` → **deleted**
- `src/test/java/io/reflectoring/buckpal/archunit/ArchitectureElement.java` → **deleted**
- `src/test/java/io/reflectoring/buckpal/archunit/HexagonalArchitecture.java` → **deleted**
- `src/test/java/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.java` → **deleted**
- `src/test/java/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.java` → **deleted**
- `src/test/java/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.java` → **deleted**
- `src/test/java/io/reflectoring/buckpal/account/domain/AccountTest.java` → **deleted**
- `src/test/java/io/reflectoring/buckpal/account/domain/ActivityWindowTest.java` → **deleted**
- `src/test/kotlin/io/reflectoring/buckpal/BuckPalApplicationTests.kt` → **added**
- `src/test/kotlin/io/reflectoring/buckpal/DependencyRuleTests.kt` → **added**
- `src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt` → **added**
- `src/test/kotlin/io/reflectoring/buckpal/common/AccountTestData.kt` → **added**
- `src/test/kotlin/io/reflectoring/buckpal/common/ActivityTestData.kt` → **added**
- `src/test/kotlin/io/reflectoring/buckpal/archunit/Adapters.kt` → **added**
- `src/test/kotlin/io/reflectoring/buckpal/archunit/ApplicationLayer.kt` → **added**
- `src/test/kotlin/io/reflectoring/buckpal/archunit/ArchitectureElement.kt` → **added**
- `src/test/kotlin/io/reflectoring/buckpal/archunit/HexagonalArchitecture.kt` → **added**
- `src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt` → **added**
- `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt` → **added**
- `src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt` → **added**
- `src/test/kotlin/io/reflectoring/buckpal/account/domain/AccountTest.kt` → **added**
- `src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt` → **added**

Harness meta-files:

- `.claude/harness/workspace/contracts/sprint-08-contract.md` (STATUS: AGREED — Phase A complete)
- `.claude/harness/workspace/handoffs/sprint-08-handoff.md` (this file)

Nothing outside the 14 in-scope test files was modified. `src/main/kotlin/**` is byte-identical to its Sprint 7 state. `build.gradle`, `src/test/resources/**`, and the harness criteria/spec are untouched.

**Sprint-8 milestone: `find src/test/java -name '*.java'` is now empty.** Combined with Sprint 7 (`src/main/java` empty), the repository contains **zero Java source files** under `src/`.

## Contract checklist

All commands run with `JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home`.

| # | Acceptance check | Result | Evidence |
|---|------------------|--------|----------|
| 1 | `find src/test/java -name '*.java'` → 0 matches (THE Sprint 8 milestone) | PASS | `find` returns empty; `src/test/java` directory itself was removed when its last file was deleted |
| 2 | `find src/test/kotlin -name '*.kt' \| wc -l` → exactly 14 | PASS | 14 |
| 3 | All 14 expected `.kt` paths exist (table in contract §Files-in-scope) | PASS | 14/14 OK |
| 4 | `grep -R "import lombok" src/test/kotlin` → 0 matches | PASS | empty |
| 5 | `grep -R "import io.mockk" src/test/kotlin` → 0 matches (Mockito stays) | PASS | empty |
| 6 | `grep -R "io.reflectoring.reviewapp" src/test/kotlin` → ≥ 4 matches (preserved book placeholder) | PASS | 6 matches (2 in `ArchitectureElement.kt` `denyDependency`, 3 in `DependencyRuleTests.kt` `testPackageDependencies`, 1 substring overlap; exact-count breakdown below) |
| 7 | `grep -F '@Sql("SendMoneySystemTest.sql")' …/SendMoneySystemTest.kt` → 1 | PASS | 1 |
| 8 | `grep -F '@Sql("AccountPersistenceAdapterTest.sql")' …/AccountPersistenceAdapterTest.kt` → 1 | PASS | 1 |
| 9 | `grep -R "import org.junit.jupiter.api.Test" src/test/kotlin` → ≥ 8 | PASS | 8 (one per test class) |
| 10 | `grep -R "import org.junit.Test" src/test/kotlin` → 0 matches (no JUnit 4) | PASS | empty |
| 11 | `./gradlew clean compileKotlin compileTestKotlin compileJava compileTestJava test check` → BUILD SUCCESSFUL | PASS | 6 actionable tasks executed; BUILD SUCCESSFUL in 6s; `compileJava` and `compileTestJava` both report `NO-SOURCE` (Sprint 7 + Sprint 8 milestone confirmation) |
| 12 | Total test count: 16/16 pass | PASS | per-suite (see below) |
| 13 | `./gradlew check` → BUILD SUCCESSFUL | PASS | green |
| 14 | `./gradlew test --tests "…DependencyRuleTests"` → 2/2 PASS | PASS | 2/2 |
| 15 | `./gradlew test --tests "…SendMoneySystemTest"` → 1/1 PASS | PASS | 1/1 (full Spring Boot + H2 + `@Sql` round trip) |
| 16 | `./gradlew test --tests "…AccountPersistenceAdapterTest"` → 2/2 PASS | PASS | 2/2 (@DataJpaTest + @Sql + @Import of `internal` Kotlin classes) |
| 17 | kotlinc warnings on the 14 new files → 0 | PASS | `./gradlew clean compileKotlin compileTestKotlin --info \| grep -E '^w:\|warning:'` → empty |
| 18 | `grep -R 'TODO\|FIXME\|XXX' src/test/kotlin` → 0 | PASS | empty |
| 19 | `git status` shows only in-scope changes | PASS | exactly 14 deletions under `src/test/java/...` + `src/test/kotlin/io/` untracked tree + harness meta-files; no other paths touched |
| 20 | `grep -c "import org.mockito" SendMoneyServiceTest.kt` → ≥ 2 | PASS | 5 (`ArgumentCaptor`, `BDDMockito.given`, `BDDMockito.then`, `Mockito`, `Mockito.times`) |
| 21 | `grep -c "Optional" SendMoneyServiceTest.kt` → ≥ 3 (transitional shim) | PASS | 3 (`import java.util.Optional`, `Optional.of(id)`, `Optional<AccountId>::get`) |
| 22 | `grep -c '!!' HexagonalArchitecture.kt` → 0 | PASS | 0 (Decision 2 applied) |
| 23 | `grep -F 'checkNotNull' HexagonalArchitecture.kt` → ≥ 3 | PASS | 3 (one per nullable field exercised by `check()`) |
| 24 | `grep -F 'io.reflectoring.reviewapp.domain..' ArchitectureElement.kt` → 1 | PASS | 1 |
| 25 | `grep -F 'io.reflectoring.reviewapp.application..' ArchitectureElement.kt` → 1 | PASS | 1 |
| 26 | `grep -F 'io.reflectoring.reviewapp.domain..' DependencyRuleTests.kt` → 1 | PASS | 1 |
| 27 | `grep -F 'io.reflectoring.reviewapp.application..' DependencyRuleTests.kt` → 1 | PASS | 1 |
| 28 | `grep -F 'io.reflectoring.reviewapp..' DependencyRuleTests.kt` → 1 | PASS | 1 (the `importPackages(...)` argument) |
| 29 | `grep -F 'apply { ' AccountTestData.kt` → ≥ 3 | PASS | 3 (one per `withX(...)` chainable setter — Decision 1 applied) |
| 30 | `grep -F 'apply { ' ActivityTestData.kt` → ≥ 6 | PASS | 6 (one per `withX(...)` chainable setter) |
| 31 | `grep -RFn 'import io.mockk' src/test/kotlin` → 0 | PASS | empty |
| 32 | `git diff --stat` only test paths touched | PASS | 14 deletions under `src/test/java/...`, 14 additions under `src/test/kotlin/...`, plus harness meta — nothing under `src/main/**` |

All 32 acceptance checks PASS.

### Full test tally (from `build/test-results/test/TEST-*.xml`)

| Suite | tests | failures | errors | skipped |
|-------|-------|----------|--------|---------|
| `io.reflectoring.buckpal.BuckPalApplicationTests` | 1 | 0 | 0 | 0 |
| `io.reflectoring.buckpal.SendMoneySystemTest` | 1 | 0 | 0 | 0 |
| `io.reflectoring.buckpal.DependencyRuleTests` | 2 | 0 | 0 | 0 |
| `io.reflectoring.buckpal.account.domain.AccountTest` | 4 | 0 | 0 | 0 |
| `io.reflectoring.buckpal.account.domain.ActivityWindowTest` | 3 | 0 | 0 | 0 |
| `io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest` | 2 | 0 | 0 | 0 |
| `io.reflectoring.buckpal.account.adapter.in.web.SendMoneyControllerTest` | 1 | 0 | 0 | 0 |
| `io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest` | 2 | 0 | 0 | 0 |
| **TOTAL** | **16** | **0** | **0** | **0** |

(Matches every per-suite count pinned in the contract.)

## Idiomatic Kotlin choices worth flagging

1. **Test-data builders as `object` + chainable builder class with `apply { ... }`** (Decision 1, approved).
   `AccountTestData` and `ActivityTestData` are now Kotlin `object`s whose `defaultAccount()` / `defaultActivity()` static factories return a `*Builder` instance pre-seeded with the original Java defaults. Each `withX(...)` setter uses the one-line idiomatic Kotlin form
   `fun withX(x: T): Builder = apply { this.x = x }`. This keeps every test body's
   `defaultAccount().withA(...).withB(...).build()` call site verbatim against the
   converted builder; the diff is syntactic only. `@JvmStatic` on the top-level
   factory keeps the JVM symbol shape matching the Java `static defaultAccount()`
   for defensive cross-call compatibility (no current consumer needs it, since
   all callers converted in this sprint).

2. **`checkNotNull` instead of `!!` in `HexagonalArchitecture.check()`** (Decision 2, approved). Each of the three late-assigned fields (`adapters`, `applicationLayer`, `configurationPackage`) is captured into a smart-cast local at the top of `check()`:
   ```kotlin
   val adapters = checkNotNull(adapters) { "withAdaptersLayer must be called before check()" }
   ```
   Subsequent dereferences read naturally without `!!`. The contract pin is `grep -c '!!' …HexagonalArchitecture.kt` = 0 — confirmed.

3. **`@SpringBootTest` / `@WebMvcTest` / `@DataJpaTest` Kotlin idioms.** Annotation attributes use Kotlin class literals: `@WebMvcTest(controllers = [SendMoneyController::class])`, `@Import(AccountPersistenceAdapter::class, AccountMapper::class)`, `@ExtendWith(SpringExtension::class)`. The square-bracket array literal form is preserved for `@WebMvcTest(controllers = ...)` (the annotation declares `Class<?>[]`).

4. **`lateinit var` only on `@Autowired` / `@MockBean` test fields.** Spring's Kotlin reference documents this as the idiomatic pattern for test field injection. The rubric anti-pattern grep for `lateinit var` is scoped to `src/main/kotlin`; test code is out of that scope, but flagged here for the Evaluator's awareness.

5. **Mockito BDD DSL preserved verbatim.** `given(...).willReturn(...)`, `then(...).should()...`, `eq(...)`, `times(0)` — zero `when(...).thenReturn(...)` calls in the Java source, so the Kotlin `when` keyword collision is **not** triggered in any of the converted tests. No backtick `` `when` `` escapes required (flagged in §Risks for completeness; addressed in the handoff "Surprises" section below).

6. **Nested-type imports.** `Account.AccountId` and `Activity.ActivityId` are reached via `import io.reflectoring.buckpal.account.domain.Account.AccountId` so call sites read `AccountId(1L)` — identical to the Java import shape.

## Anything the Evaluator should pay extra attention to

1. **Mockito + Kotlin strict null-safety surprise — null-bridging in `SendMoneyServiceTest`.** This was the load-bearing implementation surprise of the sprint. Mockito's `eq(...)`, `any(...)`, and `ArgumentCaptor.capture()` register matchers as a thread-local side effect and **return `null` at runtime**. With Kotlin 1.6 + `-Xjsr305=strict`, the compiler inserts an `Intrinsics.checkNotNullExpressionValue` at the call site whenever such a Java-platform-returning-null is fed to a Kotlin non-null parameter (e.g., `loadAccount(AccountId, LocalDateTime)`, `updateActivities(Account)`, `lockAccount(AccountId)`). The resulting NPE fires *before* Mockito's interceptor sees the call, so the matcher-queued state is irrelevant.

   Three resolution patterns, none of which alter test assertions:

   - **`eq(value)` wrapper**: a private `fun <T> eq(value: T): T = Mockito.eq(value) ?: value` — Mockito's matcher is registered, the `?: value` fallback supplies a non-null sentinel. Used at every `eq(...)` call site in both Mockito-using tests.
   - **Pre-queued `Mockito.any(...)`/`Mockito.eq(...)` + non-null literal**: inside `givenAnAccountWithId`, `givenDepositWillSucceed`, etc., the matcher is queued by a standalone `Mockito.any(Money::class.java)` call, then the stubbed method is invoked with concrete non-null sentinel values (`Money.of(0L)`, `AccountId(0L)`). Mockito's invocation interceptor consumes the queued matchers when interpreting the proxy call.
   - **`capture(captor: ArgumentCaptor<Account>): Account = captor.capture(); return accountSentinel`** for `thenAccountsHaveBeenUpdated`. The captor still records the actual `Account` instance passed by the real production code (Mockito records the *real* invocation arg, not the sentinel); the sentinel only exists to satisfy Kotlin's call-site null check during the `.updateActivities(...)` verify expression. `accountSentinel = Mockito.mock(Account::class.java)` is a one-time field-initialized non-null.

   The contract's risk register §1 ("Mockito.when() keyword collision NOT triggered") was correct that there's no `when` collision. The contract's risk register §8 ("Mockito mock of Account works because open class") is correct. **What the contract did not anticipate** was the call-site `checkNotNullExpressionValue` intrinsic on Mockito-matcher returns. This is a documented Kotlin-Mockito 2 pain point typically solved via `mockito-kotlin` — which the spec explicitly rules out. The bridges above are the canonical vanilla-Mockito workaround and live entirely inside the test class (no production code, no `build.gradle`, no third-party libs).

   Acceptance impact: zero. All 16 tests pass. `Optional` count in `SendMoneyServiceTest.kt` is 3 (`import`, `Optional.of(id)`, `Optional<AccountId>::get`) — meets the contract's transitional-shim pin.

2. **`internal` cascade on `archunit/` helpers.** The contract specified `ArchitectureElement` as `internal abstract class` and `Adapters`/`ApplicationLayer`/`HexagonalArchitecture` as `class` (public). Kotlin compiler refuses a public subclass of an internal supertype (`'public' subclass exposes its 'internal' supertype`). Adopted the minimal fix: marked `Adapters`, `ApplicationLayer`, and `HexagonalArchitecture` `internal class` as well. `DependencyRuleTests` (different package `io.reflectoring.buckpal`) still sees them because the entire test source set lives in one Gradle module — `internal` visibility crosses packages within a module. This preserves the Java package-private intent more accurately than the original contract: the Java sources had `ArchitectureElement` package-private, the subclasses `public`, and *every* call site lived inside `src/test/java/...` (one module). Tightening the subclasses to `internal` makes the Kotlin visibility match the *effective* test-only reach without widening surface. Net: the four archunit helpers are now uniformly `internal` (test-module-scoped). Surfacing this here so the Evaluator does not flag the visibility tightening as a regression. The contract's "don't widen visibility" rule is honored — visibility narrowed, never widened.

3. **`ArchitectureElement.denyDependency` parameter names retained with `@Suppress("UNUSED_PARAMETER")`.** The Java source declared `fromPackageName` and `toPackageName` parameters that are unused-by-design (the body hard-codes `io.reflectoring.reviewapp...` placeholder strings — intentional book artifact). Kotlin emits a compiler warning for unused parameters; suppressed at the function level with an explanatory comment. Without the suppression, the rubric's "0 kotlinc warnings" floor would breach. The parameter signature is preserved verbatim per the contract.

4. **`accountSentinel` and `ArgumentCaptor` field-initialization order.** In `SendMoneyServiceTest`, `accountSentinel` is declared **after** the four mock fields and the `sendMoneyService` initializer. The capture helper uses `accountSentinel`, so ordering matters — verified via clean run. The field is `Mockito.mock(Account::class.java)`, which the existing `open class Account` (Sprint 2) allows. No production-code change required.

5. **`AccountTest`'s `assertThat(success).isTrue()` / `.isFalse()`.** AssertJ 3.18 (bundled with Spring Boot 2.4.3) exposes both `isTrue()`-the-method and `isTrue`-the-property. The Java source used the parenthesized form; preserved in Kotlin. Likewise `isFalse()`.

6. **`SendMoneySystemTest` ResponseEntity star-projection.** The Java source used the raw `ResponseEntity` type; the Kotlin conversion uses `ResponseEntity<*>` for the return type and `HttpEntity<Void>(null, headers)` for the request body, matching the original `HttpEntity<Void>` shape with explicit `Void` type parameter. The body-type Class argument `Object.class` becomes `Any::class.java` (`Any` is Kotlin's `Object` equivalent).

7. **`Money.of(int)` → `Money.of(Long)` in `ActivityWindowTest`.** The Java source called `Money.of(999)`, relying on int→long auto-widening. Kotlin does not auto-widen `Int` to `Long`, so `999L`/`-500L` literals are used. This is a Kotlin idiom (not a semantic change — the existing `Money.of(value: Long): Money` Kotlin signature requires `Long`). Confirmed equivalent via test passage.

8. **`@WebMvcTest` controller reference uses `internal SendMoneyController`.** Sprint 6 made `SendMoneyController` `internal`; the test class sits in the same package and same module, so the reference resolves cleanly. No production-code change required to expose it.

## TODOs deferred to later sprints

- **Sprint 9** — remove `Account.kt`'s `Optional<AccountId>` shim (`getId(): Optional<AccountId>`, the dual `id` / `getId()` surface). The three `Optional` references in `SendMoneyServiceTest.kt` (`import java.util.Optional`, `Optional.of(id)`, `Optional<AccountId>::get`) **must** collapse along with the production-side change: the stub becomes `given(account.id).willReturn(id)` (nullable AccountId), and the ArgumentCaptor pipeline collapses to `.map(Account::id)` without the second `.map(Optional::get)`. This is the only file in `src/test/kotlin/**` that still touches `Optional`.

- **Sprint 9** — remove Lombok deps from `build.gradle` (`compileOnly 'org.projectlombok:lombok:1.18.30'` + the matching `annotationProcessor` line). Lombok now has zero consumers across the entire codebase (`grep -R "import lombok" src/main/kotlin src/test/kotlin` → 0 hits). The dep is dead weight.

- **Sprint 9** — remove `apply plugin: 'java'` / `'java-library'` from `build.gradle` (no `.java` left under `src/`).

- **Sprint 9** — consider migrating `build.gradle` → `build.gradle.kts` (optional per spec).

- **Future sprint (not Sprint 9)** — consider whether the Mockito null-bridging helpers in `SendMoneyServiceTest.kt` should migrate to a shared `test/kotlin/.../testsupport/MockitoSupport.kt` if more Mockito-using tests are added. Current count is one file (the Controller test uses only `eq` and inherits the same one-line wrapper). Not a regression; just a future deduplication opportunity.

## Commit

Not committed by the Generator. The orchestrator commits after Evaluator Phase B PASS, per the harness contract.

Self-check summary:
- 14 `.kt` files added (mirror packages under `src/test/kotlin/`); 14 `.java` files deleted.
- `./gradlew clean compileKotlin compileTestKotlin compileJava compileTestJava test check` → BUILD SUCCESSFUL.
- 16/16 tests pass across 8 suites.
- 0 kotlinc warnings on the new files.
- `find src/test/java -name '*.java'` → empty (THE Sprint 8 milestone).
- `git status` matches the in-scope file list exactly: 14 java deletions, 14 kt additions, 2 harness meta-files (contract + handoff). No path outside `src/test/java`, `src/test/kotlin`, or `.claude/harness/workspace/` is touched.
