# Sprint 8 Review

STATUS: PASS
WEIGHTED SCORE: 9.30

## Criteria

### Behavioral Correctness — 10/10 [threshold 9]

All 16 tests across 8 suites pass. ArchUnit rules green. Build is clean.

Commands re-run by the Evaluator (JAVA_HOME=corretto-17.0.13):

- `./gradlew clean compileKotlin compileTestKotlin compileJava compileTestJava` → BUILD SUCCESSFUL (`compileJava` and `compileTestJava` both report `NO-SOURCE` — Sprint 7 + Sprint 8 milestones combined; zero Java under `src/`).
- `./gradlew test` → BUILD SUCCESSFUL.
- `./gradlew check` → BUILD SUCCESSFUL (UP-TO-DATE after `test`).
- `./gradlew test --tests "io.reflectoring.buckpal.BuckPalApplicationTests"` → 1/1 PASS.
- `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"` → 1/1 PASS (full Spring Boot + H2 + `@Sql` round trip).
- `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` → 2/2 PASS.
- `./gradlew test --tests "io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest"` → 2/2 PASS.
- `./gradlew test --tests "io.reflectoring.buckpal.account.adapter.out.persistence.AccountPersistenceAdapterTest"` → 2/2 PASS.

Per-suite XML counts (`build/test-results/test/TEST-*.xml`) match the contract pins exactly:

| Suite | tests | failures | errors | skipped |
|-------|-------|----------|--------|---------|
| `BuckPalApplicationTests` | 1 | 0 | 0 | 0 |
| `SendMoneySystemTest` | 1 | 0 | 0 | 0 |
| `DependencyRuleTests` | 2 | 0 | 0 | 0 |
| `AccountTest` | 4 | 0 | 0 | 0 |
| `ActivityWindowTest` | 3 | 0 | 0 | 0 |
| `SendMoneyServiceTest` | 2 | 0 | 0 | 0 |
| `SendMoneyControllerTest` | 1 | 0 | 0 | 0 |
| `AccountPersistenceAdapterTest` | 2 | 0 | 0 | 0 |
| **TOTAL** | **16** | **0** | **0** | **0** |

**Test integrity verified.** Compared every Kotlin test against the HEAD Java source (`git show HEAD:src/test/java/.../SendMoneyServiceTest.java`). Every `@Test` method name, every `assertThat(...)` and `then(...).should()...` chain, every parameter and order, is preserved 1:1. The only delta is the call-site syntax accommodation for Mockito + Kotlin strict null-safety (analyzed under Idiomatic Kotlin / Surprise 1 below) — assertions themselves are byte-identical.

### Idiomatic Kotlin — 9/10 [threshold 7]

Concrete examples of strong idiom usage:

- `src/test/kotlin/io/reflectoring/buckpal/common/AccountTestData.kt:26-30` — chainable `withX(...): Builder = apply { ... }` is the canonical Kotlin one-line fluent setter; preserves every Java call site (`AccountTest.kt:14-27`, etc.) verbatim.
- `src/test/kotlin/io/reflectoring/buckpal/common/ActivityTestData.kt:30-40` — six `apply { ... }` setters, `id: ActivityId?` nullable parameter for `withId(null)` call in `AccountPersistenceAdapterTest.kt:42`.
- `src/test/kotlin/io/reflectoring/buckpal/archunit/HexagonalArchitecture.kt:43-52` — `checkNotNull(...) { "..." }` instead of `!!`, capturing into smart-cast locals so subsequent dereferences read naturally (`adapters.doesNotContainEmptyPackages()` on line 53 — no `!!`). Decision 2 honored exactly: 0 `!!` in this file.
- `src/test/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyControllerTest.kt:17` — `@WebMvcTest(controllers = [SendMoneyController::class])` uses Kotlin array literal + `KClass<*>` literal.
- `src/test/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/AccountPersistenceAdapterTest.kt:17` — `@Import(AccountPersistenceAdapter::class, AccountMapper::class)` clean.
- `src/test/kotlin/io/reflectoring/buckpal/common/AccountTestData.kt:8` — `object` for stateless helper (Kotlin replacement for Java `final class … { static … }`).
- `src/test/kotlin/io/reflectoring/buckpal/SendMoneySystemTest.kt:46,49` — `initialSourceBalance.minus(transferredAmount())` and `.plus(...)` — could use operator form (`-`, `+`) but the Java source used method calls, so 1:1 preservation is correct.

Mild nits (not enough to drop the score below 9):

- `src/test/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt:5,19,30,53,54` — the Java source used `Assertions.assertThat(...)` qualifier; preserved in Kotlin. A wildcard `import org.assertj.core.api.Assertions.assertThat` would be slightly more idiomatic, but the contract Decision (line 533-536) explicitly froze the qualifier for diff-minimization. Acceptable.
- `src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt:111-112` — `.map(Account::getId).map(Optional<AccountId>::get)` is a literal preservation of the Java method reference chain; only present because the `Optional<AccountId>` shim is the deliberate transitional surface (Sprint 9 removes). Flagged in Generator handoff.
- `lateinit var` for `@Autowired`/`@MockBean` test fields (4 occurrences across `SendMoneySystemTest.kt`, `SendMoneyControllerTest.kt`, `AccountPersistenceAdapterTest.kt`) — the rubric anti-pattern grep is scoped to `src/main/kotlin`; test-side `lateinit var` is the documented Spring Kotlin idiom. Not a deduction.

Anti-pattern grep results — all clean:

- `grep -R "import lombok" src/test/kotlin` → 0 matches.
- `grep -R "import io.mockk" src/test/kotlin` → 0 matches (MockK absent; Mockito retained).
- `grep -Rn "!!" src/test/kotlin` → 0 matches anywhere (the Generator solved the `HexagonalArchitecture` nullables with `checkNotNull` and used no `!!` elsewhere).
- `grep -R "Optional<" src/test/kotlin` → only in `SendMoneyServiceTest.kt` (deliberate, contract-pinned; will collapse in Sprint 9).

### Architectural Integrity — 10/10 [threshold 9]

- `./gradlew check` → BUILD SUCCESSFUL. `DependencyRuleTests` 2/2 PASS.
- Package tree under `src/test/kotlin/io/reflectoring/buckpal/...` is a mirror of the prior `src/test/java/...` layout (root, `common/`, `archunit/`, `account/adapter/in/web/`, `account/adapter/out/persistence/`, `account/application/service/`, `account/domain/`).
- ArchUnit placeholder strings preserved character-for-character: `"io.reflectoring.reviewapp.domain.."`, `"io.reflectoring.reviewapp.application.."`, `"io.reflectoring.reviewapp.."` (verified counts: 1+1 in `ArchitectureElement.kt`, 1+1+1 in `DependencyRuleTests.kt`).
- The `internal` cascade (`Adapters`, `ApplicationLayer`, `HexagonalArchitecture`, `ArchitectureElement` all `internal`) **narrows** visibility from the Java public/package-private mix. Java's effective surface was already test-source-set-only (no production-code consumer); Kotlin `internal` formalizes this. **Narrower visibility is acceptable**; no production code references any of these. `DependencyRuleTests.kt` (in `io.reflectoring.buckpal`, different package) compiles cleanly against the `internal` `HexagonalArchitecture` because both files live in the same Gradle test module.

### Code Quality — 8/10 [threshold 7]

- `./gradlew clean compileKotlin compileTestKotlin --info | grep -E '^w:|warning:'` → **0 matches**. Zero kotlinc warnings on the 14 new files.
- `grep -R 'TODO\|FIXME\|XXX' src/test/kotlin` → 0 matches.
- All 14 files have matching `filename.kt` ↔ class name.
- Imports clean; no `import *` for domain packages.
- `@Sql("SendMoneySystemTest.sql")` and `@Sql("AccountPersistenceAdapterTest.sql")` preserved verbatim (1 grep match each).
- `git status` clean: 14 deletions under `src/test/java/...`, 14 additions under `src/test/kotlin/io/...`, plus harness meta-files (`contracts/sprint-08-contract.md`, `handoffs/sprint-08-handoff.md`). Zero changes to `src/main/**` or `build.gradle`.
- `git diff --stat HEAD -- src/main` → empty (production code byte-identical to Sprint 7 state).

Minor observations (not deduction-worthy individually, sum to one point):

- `src/test/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyServiceTest.kt:166-171` — the matcher-queueing pattern in `givenAnAccountWithId` is non-obvious; the inline comment (lines 162-165) is good, but a reader still needs to understand Mockito's thread-local matcher stack. Acceptable given the constraint that `mockito-kotlin` is out of scope.
- `src/test/kotlin/io/reflectoring/buckpal/archunit/Adapters.kt:13-21` — `outgoing(...)` adds to `incomingAdapterPackages`, and `incoming(...)` adds to `outgoingAdapterPackages`. This is a faithful preservation of the Java book source (the misnomer exists in the original; intentional or not, the contract requires 1:1 preservation). Acceptable.
- Two `Mockito.any(...)` calls in `givenWithdrawalWillSucceed/Fail/givenDepositWillSucceed` (e.g., `SendMoneyServiceTest.kt:134-136`) have no inline comment explaining they queue matchers (the explanatory comment exists once in `givenAnAccountWithId`). Could benefit from a one-liner above `givenDepositWillSucceed`. Minor stylistic.

## Bugs found

| File:Line | Defect | Suggested fix |
|-----------|--------|---------------|
| — | No real defects found. | — |

## Contract checklist

All 32 acceptance checks from `sprint-08-contract.md`:

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | `find src/test/java -name '*.java'` → 0 matches | PASS | empty (directory itself removed when last file was deleted) |
| 2 | `find src/test/kotlin -name '*.kt' \| wc -l` → 14 | PASS | 14 |
| 3 | All 14 expected `.kt` paths exist | PASS | listed; 14/14 OK |
| 4 | `grep -R "import lombok" src/test/kotlin` → 0 | PASS | empty |
| 5 | `grep -R "import io.mockk" src/test/kotlin` → 0 | PASS | empty |
| 6 | `grep -R "io.reflectoring.reviewapp" src/test/kotlin` → ≥ 4 | PASS | 6 (3 strings + 1 doc comment in `ArchitectureElement.kt`; 3 strings in `DependencyRuleTests.kt`) |
| 7 | `@Sql("SendMoneySystemTest.sql")` in `SendMoneySystemTest.kt` → 1 | PASS | 1 |
| 8 | `@Sql("AccountPersistenceAdapterTest.sql")` in `AccountPersistenceAdapterTest.kt` → 1 | PASS | 1 |
| 9 | `import org.junit.jupiter.api.Test` in `src/test/kotlin` → ≥ 8 | PASS | 8 |
| 10 | `import org.junit.Test` in `src/test/kotlin` → 0 | PASS | empty |
| 11 | `./gradlew clean compileKotlin compileTestKotlin compileJava compileTestJava test check` → SUCCESS | PASS | BUILD SUCCESSFUL; `compileJava`+`compileTestJava` NO-SOURCE |
| 12 | Total test count 16/16 | PASS | XML counts confirm |
| 13 | `./gradlew check` → SUCCESS | PASS | UP-TO-DATE |
| 14 | `./gradlew test --tests "…DependencyRuleTests"` → 2/2 | PASS | 2/2 |
| 15 | `./gradlew test --tests "…SendMoneySystemTest"` → 1/1 | PASS | 1/1 |
| 16 | `./gradlew test --tests "…AccountPersistenceAdapterTest"` → 2/2 | PASS | 2/2 |
| 17 | kotlinc warnings on 14 new files → 0 | PASS | empty grep |
| 18 | `grep -R 'TODO\|FIXME\|XXX' src/test/kotlin` → 0 | PASS | empty |
| 19 | `git status` only in-scope | PASS | 14 .java deletions + `src/test/kotlin/io/` untracked + 2 harness files; nothing else |
| 20 | `grep -c "import org.mockito" SendMoneyServiceTest.kt` → ≥ 2 | PASS | 5 |
| 21 | `grep -c "Optional" SendMoneyServiceTest.kt` → ≥ 3 | PASS | 3 |
| 22 | `grep -c '!!' HexagonalArchitecture.kt` → 0 | PASS | 0 |
| 23 | `grep -F 'checkNotNull' HexagonalArchitecture.kt` → ≥ 3 | PASS | 3 |
| 24 | `'io.reflectoring.reviewapp.domain..'` in `ArchitectureElement.kt` → 1 | PASS | 1 |
| 25 | `'io.reflectoring.reviewapp.application..'` in `ArchitectureElement.kt` → 1 | PASS | 1 |
| 26 | `'io.reflectoring.reviewapp.domain..'` in `DependencyRuleTests.kt` → 1 | PASS | 1 |
| 27 | `'io.reflectoring.reviewapp.application..'` in `DependencyRuleTests.kt` → 1 | PASS | 1 |
| 28 | `'io.reflectoring.reviewapp..'` in `DependencyRuleTests.kt` → 1 | PASS | 1 |
| 29 | `'apply { '` in `AccountTestData.kt` → ≥ 3 | PASS | 3 |
| 30 | `'apply { '` in `ActivityTestData.kt` → ≥ 6 | PASS | 6 |
| 31 | `'import io.mockk'` in `src/test/kotlin` → 0 | PASS | empty |
| 32 | `git diff --stat` only test paths | PASS | 14 deletions in `src/test/java/...`; `src/main` untouched; harness meta only |

## Surprise assessment

**Surprise 1 — Mockito null-safety bridging in `SendMoneyServiceTest.kt`.** SOUND.

- The Generator's three patterns (private `eq` wrapper, pre-queued `Mockito.any(...)` + non-null sentinel, `capture(captor)` helper backed by `accountSentinel`) are the canonical vanilla-Mockito workaround for Kotlin strict null-safety. None alter Mockito's matcher semantics.
- **Test integrity verified** by diffing against `HEAD:src/test/java/.../SendMoneyServiceTest.java`: every `@Test` method body, every assertion (`assertThat(success).isFalse()`/`.isTrue()`), every `then(...).should()...` chain, every parameter ordering — preserved 1:1.
- The `private fun <T> eq(value: T): T = Mockito.eq(value) ?: value` wrapper: Mockito's `eq` registers the matcher on the thread-local stack (returning null in Java terms); the `?: value` fallback exists purely for Kotlin's non-null parameter intrinsic. Mockito's invocation interceptor consumes the queued matcher, not the runtime value. Semantically equivalent to Java.
- The pre-queued `Mockito.any(...) ; given(account.deposit(Money.of(0L), AccountId(0L)))` pattern: queues 2 matchers, invokes the mock with concrete non-null args. Mockito recognizes the matcher count matches the method arity and uses the matchers. Semantically equivalent to Java `given(account.deposit(any(Money.class), any(AccountId.class)))`.
- The `capture(captor: ArgumentCaptor<Account>): Account = captor.capture(); return accountSentinel` helper: `captor.capture()` registers the matcher AND records the actual invocation arg (Mockito records the real argument from the call being verified, not the sentinel). The `accountSentinel` only satisfies Kotlin's non-null intrinsic on `updateActivities(Account)`. Verified by the test's own assertion on line 116: `assertThat(updatedAccountIds).contains(accountId)` — this would fail if `accountSentinel`'s `getId()` (Optional.empty by default) were captured. The test passes, confirming Mockito records the real account.
- Order hazard in `givenAnAccountWithId` (lines 162-166) — the Generator correctly hoists `account.getId().get()` BEFORE queuing the `eq`/`any` matchers. Otherwise the `getId()` invocation would consume the queued matchers and Mockito would throw "0 matchers expected, 2 recorded". This is documented in an inline comment.

No test integrity concern. The bridge is restricted to the test class (no production code, no `build.gradle` change, no third-party lib introduced).

**Surprise 2 — `internal` cascade on archunit/ helpers.** ACCEPTABLE.

- Original Java: `ArchitectureElement` package-private; `Adapters`, `ApplicationLayer`, `HexagonalArchitecture` public. Effective reach: only `DependencyRuleTests.java` (different package, same test source set) consumed them via the fluent DSL. No `src/main/...` Java touched these.
- Kotlin compiler refuses a `public` subclass of an `internal` parent (`'public' subclass exposes its 'internal' supertype`). The Generator marked all four `internal class`, restoring uniformity.
- **Visibility narrowed, not widened**: `internal` (test source set, same module) is *narrower* than Java `public` and *equivalent* to (or narrower than) Java package-private (Kotlin `internal` = same module; the archunit helpers' only same-module consumer is `DependencyRuleTests`).
- `DependencyRuleTests.kt` (in `io.reflectoring.buckpal`) compiles cleanly against the `internal` `HexagonalArchitecture` (in `io.reflectoring.buckpal.archunit`) because `internal` crosses packages within one module.
- The rubric's "no widening" rule is honored.

## Verdict

The migration is mechanically clean, behaviorally exact, and idiomatically strong for the constraints imposed. All 16 tests pass, ArchUnit is green, zero kotlinc warnings, zero Lombok imports, zero MockK imports, all `io.reflectoring.reviewapp` placeholders preserved verbatim, both `@Sql` paths preserved verbatim, Decision 1 (apply-chain builders) and Decision 2 (`checkNotNull` over `!!`) both implemented exactly. The two Generator-flagged surprises (Mockito null-bridge, `internal` cascade) are both sound, and crucially neither weakens any assertion. `git status` shows only the 14 in-scope file deletions plus the matching 14 Kotlin additions plus the 2 harness meta-files; `src/main/**` is byte-identical to its Sprint 7 state. Sprint-8 milestone (`find src/test/java -name '*.java'` empty) achieved.

Generator may now commit sprint 8.
