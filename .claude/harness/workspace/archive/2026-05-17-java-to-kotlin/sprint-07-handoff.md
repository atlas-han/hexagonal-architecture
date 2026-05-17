# Sprint 7 Handoff — Root Spring Boot setup

## What changed

Production conversions (3 .java deleted → 3 .kt added):

- `src/main/java/io/reflectoring/buckpal/BuckPalApplication.java` → **deleted**
- `src/main/java/io/reflectoring/buckpal/BuckPalConfiguration.java` → **deleted**
- `src/main/java/io/reflectoring/buckpal/BuckPalConfigurationProperties.java` → **deleted**
- `src/main/kotlin/io/reflectoring/buckpal/BuckPalApplication.kt` → **added** (top-level `main` + `@SpringBootApplication class BuckPalApplication`)
- `src/main/kotlin/io/reflectoring/buckpal/BuckPalConfiguration.kt` → **added** (single `@Bean` factory `moneyTransferProperties` returning Sprint 4's `MoneyTransferProperties`)
- `src/main/kotlin/io/reflectoring/buckpal/BuckPalConfigurationProperties.kt` → **added** (`data class` with `var transferThreshold: Long = Long.MAX_VALUE`; `@ConfigurationProperties(prefix = "buckpal")`; **no `@ConstructorBinding`**)

Harness meta-files:

- `.claude/harness/workspace/contracts/sprint-07-contract.md` (STATUS: AGREED)
- `.claude/harness/workspace/handoffs/sprint-07-handoff.md` (this file)

Nothing outside the 3 in-scope production files was modified. `build.gradle`, `application.yml`, all Sprint 1–6 Kotlin code, and the entire test tree (still `.java`) are byte-identical.

**Sprint-7 milestone: `find src/main/java -name '*.java'` is now empty.** The migration's main production source tree has zero Java files.

## Contract checklist

All commands run with `JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home`.

| # | Acceptance check | Result | Evidence |
|---|------------------|--------|----------|
| 1 | `find src/main/java -name '*.java'` → empty | PASS | command returned no output |
| 2 | `find src/main/java/io/reflectoring/buckpal -maxdepth 1 -name '*.java'` → 0 | PASS | empty (subset of #1) |
| 3 | `find src/main/kotlin/io/reflectoring/buckpal -maxdepth 1 -name '*.kt'` → exactly 3 | PASS | `BuckPalApplication.kt`, `BuckPalConfiguration.kt`, `BuckPalConfigurationProperties.kt` |
| 4 | `grep -R "import lombok" src/main/kotlin/io/reflectoring/buckpal` → 0 matches | PASS | empty output (no Lombok import anywhere in Kotlin tree) |
| 5 | `grep -R "@ConstructorBinding" src/main/kotlin/io/reflectoring/buckpal` → 0 matches | PASS | empty output (JavaBeans-style binding only, per spec) |
| 6 | `grep -E "(lateinit\|@Autowired)" <3 new files>` → 0 matches | PASS | empty output |
| 7 | `grep -F "!!" <3 new files>` → 0 matches | PASS | empty output |
| 8 | `grep -R "Optional<" <3 new files>` → 0 matches | PASS | empty output |
| 9 | `grep "@SpringBootApplication" BuckPalApplication.kt` → 1 | PASS | 1 match on line 5 |
| 10 | `grep "runApplication<BuckPalApplication>" BuckPalApplication.kt` → 1 | PASS | 1 match on line 9 |
| 11 | `grep "@Configuration" BuckPalConfiguration.kt` → 1 | PASS | 1 match on line 12 |
| 12 | `grep "@EnableConfigurationProperties(BuckPalConfigurationProperties::class)" BuckPalConfiguration.kt` → 1 | PASS | 1 match on line 13 |
| 13 | `grep "@ConfigurationProperties(prefix = \"buckpal\")" BuckPalConfigurationProperties.kt` → 1 | PASS | 1 match on line 5 |
| 14 | `grep "var transferThreshold: Long = Long.MAX_VALUE" BuckPalConfigurationProperties.kt` → 1 | PASS | 1 match on line 7 |
| 15 | `grep "data class BuckPalConfigurationProperties" BuckPalConfigurationProperties.kt` → 1 | PASS | 1 match on line 6 |
| 16 | `./gradlew clean compileKotlin compileTestKotlin compileJava compileTestJava test check` → BUILD SUCCESSFUL, all tests pass | PASS | BUILD SUCCESSFUL in 5s; all 7 actionable tasks executed; `compileJava NO-SOURCE` (expected — no `.java` in `src/main/java` anymore); 16 tests across 8 suites green |
| 17 | `./gradlew test --tests "...BuckPalApplicationTests"` → PASS | PASS | 1/1 — `contextLoads()` boots the full SpringBootTest context, proving `@SpringBootApplication`, `@EnableConfigurationProperties`, `@ConfigurationProperties` binding, and the `@Bean` factory all wire correctly |
| 18 | `./gradlew test --tests "...SendMoneySystemTest"` → PASS | PASS | 1/1 — full Spring Boot + H2 + HTTP path through `SendMoneyController` → `SendMoneyService` (the latter consumes the `MoneyTransferProperties` bean from the converted `BuckPalConfiguration.moneyTransferProperties`) |
| 19 | `./gradlew test --tests "...DependencyRuleTests"` → PASS | PASS | 2/2 — ArchUnit green; `io.reflectoring.buckpal.**` package layout intact |
| 20 | kotlinc warnings on the 3 new files → 0 | PASS | `./gradlew clean compileKotlin --info \| grep -E '^w:\|warning:'` → 0 hits |
| 21 | `grep -R 'TODO\|FIXME\|XXX' <3 new files>` → 0 matches | PASS | empty output |
| 22 | `git status` shows only in-scope changes (3 java deletions + 3 kt additions + contract/handoff meta-files) | PASS | exactly the expected 7 paths (3 staged deletions + 4 untracked, two of which are the harness meta-files) |

All 22 acceptance checks PASS.

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

## Idiomatic Kotlin choices worth flagging

1. **Top-level `main` function** with the Spring Boot Kotlin extension
   `runApplication<BuckPalApplication>(*args)` (import
   `org.springframework.boot.runApplication`). This is the documented
   idiomatic Kotlin entry point — replaces `SpringApplication.run(Class, String[])`
   with a one-liner using a reified type parameter. Bytecode is equivalent;
   the `BuckPalApplicationKt` synthesized class provides the
   `public static void main(String[])` bridge the JVM/Spring Boot launcher
   needs.
2. **`data class BuckPalConfigurationProperties(var transferThreshold: Long = Long.MAX_VALUE)`**
   — the Kotlin equivalent of the Java `@Data class BuckPalConfigurationProperties { private long transferThreshold = Long.MAX_VALUE; }`.
   `var` synthesizes the JavaBeans setter `setTransferThreshold(long)` that
   Spring Boot 2.4.3's `@ConfigurationProperties` binder uses. `data class`
   gives `equals`/`hashCode`/`toString`/`copy`/`componentN` for free.
3. **No `@ConstructorBinding`** — deliberately omitted per spec risk register
   #8 and Sprint 7 spec note. JavaBeans-style binding via `var` setter is
   the path Spring Boot 2.4.3 supports out of the box and matches the
   semantics of the original `@Data`-generated setter. Adding
   `@ConstructorBinding` would change binding mechanics (requires the
   property as constructor parameter, no setter usage) for zero benefit
   here.
4. **`::class` class-literal** in `@EnableConfigurationProperties(BuckPalConfigurationProperties::class)`
   — Kotlin's idiomatic alternative to Java's `.class`. The compiler
   transparently converts the `KClass` to a JVM `Class` literal for the
   annotation argument; no `.java` suffix needed.
5. **Single-expression `@Bean` function** (`@Bean fun moneyTransferProperties(...): MoneyTransferProperties = MoneyTransferProperties(Money.of(...))`)
   — one-expression body uses `=` instead of `{ return ... }`.
6. **Property access** (`buckPalConfigurationProperties.transferThreshold`)
   replaces Lombok-generated getter call (`.getTransferThreshold()`).
7. **No `internal`** on these 3 types — the original Java was `public class`
   for all three (root-package classes, top-level `main`). Preserved
   verbatim; kotlin-spring opens `BuckPalConfiguration` and
   `BuckPalApplication` automatically for CGLIB.

## Anything the Evaluator should pay extra attention to

1. **`BuckPalApplicationTests` is still `.java`** (Sprint 8). It's now the
   load-bearing live evidence that the entire `@SpringBootApplication` +
   `@EnableConfigurationProperties` + `@Bean`-factory wiring works against
   the Kotlin classes. If Spring couldn't find `BuckPalApplication`, or if
   `@ConfigurationProperties` failed to bind, or if the `@Bean` factory
   couldn't construct `MoneyTransferProperties`, this test would fail with
   `ApplicationContextException`. It passes (1/1) — strongest available
   smoke for Sprint 7 in CI. `./gradlew bootRun` smoke was deliberately
   skipped (brittle, time-bounded, not deterministic) per spec.
2. **`SendMoneySystemTest` exercises the full HTTP path** through the
   converted root configuration. It loads `application.yml`
   (`buckpal.transferThreshold: 10000`) → bound onto
   `BuckPalConfigurationProperties.transferThreshold` via JavaBeans setter
   → consumed by `BuckPalConfiguration.moneyTransferProperties(...)` →
   produces `MoneyTransferProperties(Money.of(10000))` bean → injected into
   `SendMoneyService`. If `var transferThreshold` setter weren't synthesized
   correctly, the field would silently stay at `Long.MAX_VALUE` and the
   `MoneyTransferProperties` bean would have `Money.of(Long.MAX_VALUE)` —
   the existing transfer test (500 units) would still pass because the
   threshold check is `>`, not `==`. So the live binding probe is
   reasonable-but-not-pinpoint. (Adding a pinpoint binding test is a
   Sprint-8 follow-up if desired.)
3. **`@Bean` method name preserved exactly** (`moneyTransferProperties`) —
   Spring uses the method name as the bean id by default. Any consumer that
   `@Autowired` by bean name would silently break if the method were
   renamed. (None exist in this codebase — all injections are by type — but
   the name is preserved as a defensive measure.)
4. **Package preserved exactly** at `io.reflectoring.buckpal`. The
   `@SpringBootApplication` default component-scan root is the annotated
   class's package, so the scan finds all `@WebAdapter`,
   `@PersistenceAdapter`, `@UseCase` beans under `io.reflectoring.buckpal.account.**`.
5. **`compileJava` task reports `NO-SOURCE`** in the build output — this is
   the expected new state after Sprint 7. There are no production `.java`
   files left to compile. Test `.java` files compile via `compileTestJava`
   (still active until Sprint 8).
6. **Both `BuckPalConfiguration` and `BuckPalApplication` are `class` (not
   `data class` or `object`).** `@Configuration` and
   `@SpringBootApplication` need a regular `class` that kotlin-spring can
   make `open` for CGLIB proxying. `data class` would be wrong
   (synthesizes `equals`/`hashCode` based on properties — these classes have
   none) and `object` would prevent CGLIB subclassing.
7. **Zero kotlinc warnings** on the 3 new files; `./gradlew check` green;
   `git diff` shows exactly 6 paths (3 java deleted, 3 kt added).

## TODOs deferred to later sprints

- **Sprint 8** — convert `BuckPalApplicationTests.java`,
  `DependencyRuleTests.java`, `SendMoneySystemTest.java`, all
  `account/...` tests, and the ArchUnit / test-data helper classes.
- **Sprint 9** — remove Lombok deps from `build.gradle`
  (`compileOnly 'org.projectlombok:lombok:1.18.30'` and
  `annotationProcessor` line); remove `apply plugin: 'java'` /
  `'java-library'` if Gradle no longer needs them; optionally migrate to
  `build.gradle.kts`.
- **Sprint 9** — remove `Account.kt`'s `Optional<AccountId>` shim
  (`getId(): Optional<AccountId>`) once Sprint 8 has converted all the
  Java tests that consume it.

## Commit

Not committed by the Generator. The orchestrator commits after Evaluator
Phase B PASS, per the harness contract.

Self-check summary:
- 3 `.kt` files added, 3 `.java` files deleted (`git rm`).
- `./gradlew clean compileKotlin compileTestKotlin compileJava compileTestJava test check` → BUILD SUCCESSFUL.
- 16/16 tests pass across 8 suites.
- 0 kotlinc warnings.
- `find src/main/java -name '*.java'` → empty (the Sprint 7 milestone).
- `git status` matches the in-scope file list exactly.
