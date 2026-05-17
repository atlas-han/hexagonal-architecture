# Sprint 4 Review

STATUS: PASS
WEIGHTED SCORE: 9.25

## Criteria

### Behavioral Correctness — 10/10 [threshold 9]

All independently re-executed commands green:

- `./gradlew clean` → BUILD SUCCESSFUL.
- `./gradlew compileKotlin compileJava compileTestKotlin compileTestJava` → BUILD SUCCESSFUL, no warnings.
- `./gradlew test` → BUILD SUCCESSFUL. Per-suite (JUnit XML attributes):
  - `SendMoneyControllerTest` — tests=1, failures=0, errors=0
  - `AccountPersistenceAdapterTest` — tests=2, failures=0, errors=0
  - `SendMoneyServiceTest` — tests=2, failures=0, errors=0  (the suite the Account.kt scope expansion was needed for)
  - `AccountTest` — tests=4, failures=0, errors=0
  - `ActivityWindowTest` — tests=3, failures=0, errors=0
  - `BuckPalApplicationTests` — tests=1, failures=0, errors=0  (Spring context boots end-to-end with Kotlin services)
  - `DependencyRuleTests` — tests=2, failures=0, errors=0  (ArchUnit Hexagonal rules green)
  - `SendMoneySystemTest` — tests=1, failures=0, errors=0  (full vertical slice POST /accounts/send/1/2/500 over H2)
  - **Total: 16/16 PASS.**
- `./gradlew check` → BUILD SUCCESSFUL.

The `SendMoneySystemTest` PASS is the load-bearing signal: controller → service (now Kotlin) → port → adapter → JPA → H2 with `@Transactional` proxying all work. The kotlin-spring plugin successfully opened `SendMoneyService` for CGLIB (verified via javap — `public class` not `final`). `NoOpAccountLock`'s `@Component` is wired (system test would otherwise fail with `NoSuchBeanDefinitionException` for the `AccountLock` constructor parameter).

### Idiomatic Kotlin — 9/10 [threshold 7]

Anti-pattern greps in `src/main/kotlin/.../service/`:
- `import lombok` — 0 hits
- `!!` / `lateinit` / `@Autowired` — 0 hits
- `Optional` — 0 hits

Positive idioms observed (file:line):
- `SendMoneyService.kt:14-19` — primary-constructor injection, all `private val`. No `@Autowired`.
- `SendMoneyService.kt:29-32` — `?: error("expected ... not to be empty")` used at the two id-resolution points (2 occurrences as contracted). Throws `IllegalStateException` matching the original Java `orElseThrow(IllegalStateException::new)` semantics.
- `GetAccountBalanceService.kt:13-14` — single-expression function with `=` body for the one-liner query implementation.
- `MoneyTransferProperties.kt:8-10` — `data class` with `@JvmOverloads constructor(... = Money.of(1_000_000L))`. `javap` confirms two ctors emitted: `(Money)` AND `()`. (The `var` is deliberate; it preserves the `@Data`-equivalent setter affordance.)
- `NoOpAccountLock.kt:7-17` — `@Component internal class`; trailing-comma style absent but consistent with the rest of the package.
- `ThresholdExceededException.kt:5-7` — Kotlin string template `"... $actual but threshold is $threshold!"` replacing `String.format`. Output byte-identical because both call `Money.toString()`.

Minor deductions:
- `Account.kt:26` introduces a custom getter routing through `getId().orElse(null)` — strictly less idiomatic than a plain primary-ctor `val id: AccountId?`. Acknowledged as transitional in the KDoc; will collapse Sprint 9.
- `Account.kt:55,78` retain `_id!!` (now on the renamed backing field). These have a justifying comment as required by the criteria.

### Architectural Integrity — 10/10 [threshold 9]

- `DependencyRuleTests` (ArchUnit `HexagonalArchitecture`) PASS — 2/2.
- Package layout under `src/main/kotlin/.../account/application/service/` matches the original Java layout exactly (5 .kt files in the same package).
- Imports in the 5 new files are confined to: `domain`, `application.port.in`, `application.port.out`, `common.UseCase`, `org.springframework.stereotype.Component`, `javax.transaction.Transactional`, `java.time.LocalDateTime`. No adapter-layer leakage; no `domain` importing `application` or `adapter`.
- Visibility decisions match the original Java: `SendMoneyService` public (matches Java `public class`), `GetAccountBalanceService` / `NoOpAccountLock` `internal` (matches Java package-private), `ThresholdExceededException` / `MoneyTransferProperties` public.

### Code Quality — 8/10 [threshold 7]

- 0 kotlinc warnings on `./gradlew compileKotlin --rerun-tasks --warning-mode all`.
- File-per-class; filenames match content.
- KDoc present on `MoneyTransferProperties` and on the new `Account.id` custom getter explaining the transitional routing.
- No commented-out code; no orphan `TODO` (the Sprint 8/9 follow-ups are tracked in the handoff's "TODOs deferred").
- `Account.kt` now carries one extra layer of indirection (`id` → `getId()` → `Optional.orElse(null)`) compared to the Sprint 2 result. Trivial perf impact, but it is a temporary increase in cognitive load. The KDoc mitigates this — the reader is told why.
- Minor nit: `SendMoneyService.kt` uses backticked `\`in\`` for the reserved-word package — unavoidable, but pulls the eye on lines 3-4.

## Bugs found

None. No defects found that would block sprint acceptance.

| File:Line | Defect | Suggested fix |
|-----------|--------|---------------|
| — | — | — |

## Contract checklist

- [PASS] `find src/main/java/.../service -name '*.java'` → 0. Evidence: `find` output is empty.
- [PASS] `find src/main/kotlin/.../service -name '*.kt'` → 5. Evidence: SendMoneyService.kt, GetAccountBalanceService.kt, MoneyTransferProperties.kt, NoOpAccountLock.kt, ThresholdExceededException.kt.
- [PASS] `grep -R "import lombok" src/main/kotlin/.../service` → 0 hits.
- [PASS] anti-pattern grep `(!!|lateinit|@Autowired)` in service/ → 0 hits.
- [PASS] `grep -R "Optional" src/main/kotlin/.../service` → 0 hits.
- [PASS] `grep -c "?: error(" SendMoneyService.kt` → 2 (source + target).
- [PASS] `grep -c "@JvmOverloads" MoneyTransferProperties.kt` → 1.
- [PASS] `./gradlew clean compileKotlin compileJava compileTestKotlin compileTestJava` → BUILD SUCCESSFUL.
- [PASS] `./gradlew test` → BUILD SUCCESSFUL, 16/16 tests pass.
- [PASS] `./gradlew test --tests SendMoneyServiceTest` → 2/2 (XML report).
- [PASS] `./gradlew test --tests SendMoneySystemTest` → 1/1 (XML report).
- [PASS] `./gradlew test --tests BuckPalApplicationTests` → 1/1 (XML report).
- [PASS] `./gradlew test --tests DependencyRuleTests` → 2/2 (XML report — ArchUnit Hexagonal rules green).
- [PASS] kotlinc warnings on the 5 new files → 0. `./gradlew compileKotlin --rerun-tasks --warning-mode all` only emits a Gradle 7 deprecation note; no Kotlin compiler warnings on the converted files.
- [PASS] `javap -p SendMoneyService.class` → `public class ... SendMoneyService implements ... SendMoneyUseCase` (NOT `final`). kotlin-spring opened it for `@Transactional` CGLIB.

Additional Evaluator-only verifications:

- [PASS] `javap -p Account.class` → exposes BOTH `getIdOrNull()` and `getId(): Optional<AccountId>` — Sprint 2's `@get:JvmName("getIdOrNull")` contract preserved.
- [PASS] `javap -p MoneyTransferProperties.class` → has BOTH `<init>(Money)` AND a synthetic no-arg `<init>()` ctor — `@JvmOverloads` working.
- [PASS] `javap -p NoOpAccountLock.class` → `public class` (not `final`), `implements AccountLock` — visible to component scan even though declared `internal` in Kotlin.
- [PASS] `git diff src/test` → empty. No tests were modified.
- [PASS] `git status` — only 5 .java deletions, 1 .kt modification (Account.kt — disclosed in handoff), and 5 new .kt files in the service/ directory.

## Account.kt scope-expansion judgment

The generator self-declared in the handoff that `Account.kt` was edited outside the strict sprint scope. Per evaluator.md: *"If git diff shows a file the Generator didn't mention in the handoff, that is automatically a FAIL."* The file IS mentioned, with rationale and a removal plan. The literal auto-FAIL clause does not apply. Judgment call:

- (a) **Load-bearing for behavior parity:** YES. `SendMoneyServiceTest.java` mocks `account.getId()` and expects the Kotlin service to use that result. Without routing the property through `getId()`, the Mockito-stubbed Optional would not drive `account.id` reads → `?: error(...)` would fire → 2 tests fail. The change is the smallest possible fix that preserves test behavior without touching the test (modifying the test would itself be an auto-FAIL).
- (b) **Minimal:** YES. 6-line surgical edit: rename backing field to `_id`, add a custom getter, change two `id!!` references to `_id!!`. JVM signatures are identical (`javap` confirms `getId()` and `getIdOrNull()` shapes unchanged from Sprint 2's contract).
- (c) **Documented with explicit removal plan:** YES. The new property's KDoc explicitly says "Once Sprint 8 converts the tests, this routing can collapse back to a plain primary-ctor `val id: AccountId?`." The handoff also lists this under "TODOs deferred to later sprints" pointing to Sprint 9.

**Verdict: PASS-with-note.** The expansion is necessary, minimal, transparent, and reversible. Failing the sprint here would punish honest disclosure of an unavoidable transitional concern, the opposite of what the auto-FAIL rule is designed to deter (silent scope creep). Note recorded for Sprint 9 to verify removal.

## Verdict

Sprint 4 PASSES with high confidence. All four criteria clear their hard floors (BC 10/9, IK 9/7, AI 10/9, CQ 8/7); weighted score 9.25. The five new Kotlin service files are idiomatic, the `kotlin-spring` plugin correctly opens `SendMoneyService` for `@Transactional` CGLIB proxying (javap-verified non-final), `@JvmOverloads` exposes both 0-arg and 1-arg `MoneyTransferProperties` constructors as required by Java callers, and the full vertical-slice `SendMoneySystemTest` plus `BuckPalApplicationTests` confirm the Kotlin services wire into the Spring context end-to-end over H2. The Account.kt scope expansion is accepted as a transparent, minimal, time-boxed transitional measure with an explicit Sprint 9 removal plan. Sprint 9 must verify that the `_id`-backing-field + custom-getter routing is collapsed back once tests are Kotlinized.
