STATUS: AGREED
// EVALUATOR: Phase A reviewed 2026-05-17. Contract is complete, mechanical,
// and verifiable. All 4 rubric criteria covered explicitly. The
// `@SpringBootTest` (BuckPalApplicationTests) is correctly identified as the
// load-bearing wiring probe — replaces the spec's brittle `./gradlew bootRun`
// smoke. `@ConstructorBinding` is correctly excluded per spec risk register
// #8. No inline edits needed. Generator: proceed to implementation.

# Sprint 7 Contract — Root Spring Boot setup

**Status:** DRAFT (awaiting Evaluator Phase A review)
**Generator:** main session
**Sprint goal (from spec):** Convert the 3 root-level classes
(`BuckPalApplication`, `BuckPalConfiguration`, `BuckPalConfigurationProperties`)
to Kotlin. After this sprint, `find src/main/java -name '*.java'` must be empty.

## Files in scope

Production (3 .java → 3 .kt):

| Java file | Kotlin equivalent |
|-----------|-------------------|
| `src/main/java/io/reflectoring/buckpal/BuckPalApplication.java`               | `src/main/kotlin/io/reflectoring/buckpal/BuckPalApplication.kt` |
| `src/main/java/io/reflectoring/buckpal/BuckPalConfiguration.java`             | `src/main/kotlin/io/reflectoring/buckpal/BuckPalConfiguration.kt` |
| `src/main/java/io/reflectoring/buckpal/BuckPalConfigurationProperties.java`   | `src/main/kotlin/io/reflectoring/buckpal/BuckPalConfigurationProperties.kt` |

Nothing else is touched. In particular:
- `application.yml` (`src/main/resources/application.yml`) — unchanged. Its
  `buckpal.transferThreshold: 10000` key is the live wiring probe for the
  `@ConfigurationProperties(prefix = "buckpal")` binding and must keep
  resolving via the JavaBeans-style `var transferThreshold` setter on the
  new Kotlin data class.
- `MoneyTransferProperties.kt` (Sprint 4 output) — unchanged. The Sprint 4
  `data class MoneyTransferProperties(var maximumTransferThreshold: Money = Money.of(1_000_000L))`
  is invoked from the new `BuckPalConfiguration.kt` `@Bean` method with the
  resolved `Money.of(transferThreshold)` value, exactly as the Java code does.
- `BuckPalApplicationTests.java` (the `@SpringBootTest` context-load probe) —
  unchanged. It is still `.java` until Sprint 8; it must continue to compile
  against and exercise the new Kotlin root classes. Its passing run is the
  load-bearing live evidence that `@SpringBootApplication`, the
  `@Configuration`+`@EnableConfigurationProperties` wiring, and the
  `@ConfigurationProperties` binding all still work end-to-end.
- Build configuration (`build.gradle`) — unchanged. Lombok is still a
  `compileOnly`+`annotationProcessor` dependency (Sprint 9 cleanup); after
  this sprint it should have zero consumers in `src/main`. We do NOT remove
  the dependency yet, but its removal becomes purely mechanical from here.

## Conversion targets

### 1. `BuckPalApplication.kt`

- **File shape:** top-level `main` function + `@SpringBootApplication class BuckPalApplication`.
- **Type kind:** plain `class`; package-level (`public` in Kotlin = no
  modifier needed). kotlin-spring plugin will `open` it automatically.
- **Shape:**

```kotlin
package io.reflectoring.buckpal

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BuckPalApplication

fun main(args: Array<String>) {
    runApplication<BuckPalApplication>(*args)
}
```

Key conversion points:
- Idiomatic Kotlin Spring Boot entry: `runApplication<T>(*args)` is the
  documented Spring Boot Kotlin extension (`org.springframework.boot.runApplication`).
  It expands to `SpringApplication.run(BuckPalApplication::class.java, *args)`
  — same bytecode entry path as the Java version.
- The `main` is **top-level**, not a `companion object @JvmStatic fun main`.
  Spring Boot's Maven/Gradle launcher discovers top-level Kotlin `main`
  via the synthesized `BuckPalApplicationKt` class (containing a
  `public static void main(String[])` bridge). `bootRun` and the
  `bootJar`'s `Main-Class` manifest will find it.
- The class body is empty — no need for `{}` braces.
- `package` line preserved exactly.

### 2. `BuckPalConfiguration.kt`

- **Type kind:** plain `class`; public; kotlin-spring opens it for CGLIB.
- **Annotations preserved:** `@Configuration`, `@EnableConfigurationProperties(BuckPalConfigurationProperties::class)`.
- **`@Bean` method signature preserved:** the `moneyTransferProperties(...)`
  method takes a `BuckPalConfigurationProperties` parameter (Spring injects
  it) and returns a `MoneyTransferProperties` constructed with
  `Money.of(buckPalConfigurationProperties.transferThreshold)`.
- **Shape:**

```kotlin
package io.reflectoring.buckpal

import io.reflectoring.buckpal.account.application.service.MoneyTransferProperties
import io.reflectoring.buckpal.account.domain.Money
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration that exposes use-case-specific beans derived from
 * the Spring-Boot-bound [BuckPalConfigurationProperties].
 */
@Configuration
@EnableConfigurationProperties(BuckPalConfigurationProperties::class)
class BuckPalConfiguration {

    /**
     * Adds a use-case-specific [MoneyTransferProperties] bean to the
     * application context. The properties are read from the Spring-Boot-
     * specific [BuckPalConfigurationProperties] object.
     */
    @Bean
    fun moneyTransferProperties(
        buckPalConfigurationProperties: BuckPalConfigurationProperties,
    ): MoneyTransferProperties =
        MoneyTransferProperties(
            Money.of(buckPalConfigurationProperties.transferThreshold),
        )
}
```

Key conversion points:
- `BuckPalConfigurationProperties.class` → `BuckPalConfigurationProperties::class`
  (Kotlin class literal; Spring's `@EnableConfigurationProperties` accepts
  `KClass<out Annotation>`-style arrays through its `value` attribute).
- `getTransferThreshold()` → `transferThreshold` (Kotlin property access on
  the `var transferThreshold: Long` field of the new data class).
- `@Bean fun moneyTransferProperties(...)` — kept exactly the same method
  name and parameter name; Spring resolves bean candidates by parameter
  name only when explicitly enabled (it's not the default), so the
  resolution remains by parameter **type** (`BuckPalConfigurationProperties`),
  matching Java semantics.
- The `@Bean` method is **not declared `internal`** — `@Bean` factory
  methods on a public `@Configuration` class must remain public so Spring
  can invoke them via the CGLIB-proxied configuration class. (kotlin-spring
  opens the class; the method's default visibility is `public`.)
- Javadoc preserved as a KDoc block (`/** ... */`) with `[ClassName]`
  cross-references in place of `{@link ...}`.

### 3. `BuckPalConfigurationProperties.kt`

- **Type kind:** `data class`; public.
- **Single field:** `var transferThreshold: Long = Long.MAX_VALUE`.
  - `var` (not `val`) — Spring Boot 2.4.3 binds `@ConfigurationProperties`
    via JavaBeans-style setters by default. The Kotlin `var` synthesizes
    a `public void setTransferThreshold(long)` setter on the JVM, which
    Spring's binder discovers via reflection. (Constructor binding via
    `@ConstructorBinding` is also available in Boot 2.4.3 but per the
    spec risk register entry #8 and the Sprint 7 spec note we **do NOT**
    add `@ConstructorBinding` — JavaBeans-style binding works and is
    what the original `@Data`+`private long transferThreshold` Java
    class relied on. Adding `@ConstructorBinding` would change the
    binding semantics and is unnecessary risk.)
  - `Long.MAX_VALUE` default preserves the Java default
    (`private long transferThreshold = Long.MAX_VALUE`). When
    `application.yml` provides `buckpal.transferThreshold: 10000`,
    Spring overrides the default at bind time; the test profile
    (no override) keeps the `Long.MAX_VALUE` default. Same as Java.
- **Annotations preserved:** `@ConfigurationProperties(prefix = "buckpal")`.
- **Shape:**

```kotlin
package io.reflectoring.buckpal

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "buckpal")
data class BuckPalConfigurationProperties(
    var transferThreshold: Long = Long.MAX_VALUE,
)
```

Key conversion points:
- `@Data` Lombok annotation removed — `data class` synthesizes equals,
  hashCode, toString, copy, and `componentN` accessors. The `var`
  property also synthesizes JavaBeans getter+setter
  (`getTransferThreshold()`/`setTransferThreshold(long)`) for Spring's
  binder.
- `data class` vs plain `class`: `data class` is acceptable because
  `BuckPalConfigurationProperties` is a small value-bearing config bag
  with one field, exactly the shape `data class` is designed for. The
  original Java was `@Data`-annotated — the Kotlin equivalent of
  `@Data` is `data class`. The fact that Spring's binder mutates the
  field via the setter does not preclude `data class` (the data-class
  contract requires `copy()`/`componentN()`/`equals`/`hashCode` based
  on properties, not immutability).
- **No `@ConstructorBinding`** — per the spec (Sprint 7 conversion
  notes) and the risk register (#8). JavaBeans-style binding via the
  `var` setter is the default in Boot 2.4.3 and matches the original
  Java behavior.
- Default value `Long.MAX_VALUE` — preserves Java behavior when no
  `buckpal.transferThreshold` key is present in the active config.

## Acceptance checks

- [ ] `find src/main/java -name '*.java'` → **empty** (the load-bearing
  end-state check from the spec; after this sprint there must be zero
  production `.java` files anywhere under `src/main/java`)
- [ ] `find src/main/java/io/reflectoring/buckpal -maxdepth 1 -name '*.java'` → 0
- [ ] `find src/main/kotlin/io/reflectoring/buckpal -maxdepth 1 -name '*.kt'` → exactly 3
  (`BuckPalApplication.kt`, `BuckPalConfiguration.kt`, `BuckPalConfigurationProperties.kt`)
- [ ] `grep -R "import lombok" src/main/kotlin/io/reflectoring/buckpal` → 0 matches
  (the Lombok `@Data` import from the Java original is removed)
- [ ] `grep -R "@ConstructorBinding" src/main/kotlin/io/reflectoring/buckpal` → 0 matches
  (deliberate: JavaBeans-style binding only, per spec)
- [ ] `grep -E "(lateinit|@Autowired)" src/main/kotlin/io/reflectoring/buckpal/BuckPalApplication.kt src/main/kotlin/io/reflectoring/buckpal/BuckPalConfiguration.kt src/main/kotlin/io/reflectoring/buckpal/BuckPalConfigurationProperties.kt` → 0 matches
- [ ] `grep -R "!!" src/main/kotlin/io/reflectoring/buckpal/BuckPalApplication.kt src/main/kotlin/io/reflectoring/buckpal/BuckPalConfiguration.kt src/main/kotlin/io/reflectoring/buckpal/BuckPalConfigurationProperties.kt` → 0 matches
- [ ] `grep -R "Optional<" src/main/kotlin/io/reflectoring/buckpal/BuckPalApplication.kt src/main/kotlin/io/reflectoring/buckpal/BuckPalConfiguration.kt src/main/kotlin/io/reflectoring/buckpal/BuckPalConfigurationProperties.kt` → 0 matches
- [ ] `grep "@SpringBootApplication" src/main/kotlin/io/reflectoring/buckpal/BuckPalApplication.kt` → 1 match
- [ ] `grep "runApplication<BuckPalApplication>" src/main/kotlin/io/reflectoring/buckpal/BuckPalApplication.kt` → 1 match
- [ ] `grep "@Configuration" src/main/kotlin/io/reflectoring/buckpal/BuckPalConfiguration.kt` → 1 match
- [ ] `grep "@EnableConfigurationProperties(BuckPalConfigurationProperties::class)" src/main/kotlin/io/reflectoring/buckpal/BuckPalConfiguration.kt` → 1 match
- [ ] `grep "@ConfigurationProperties(prefix = \"buckpal\")" src/main/kotlin/io/reflectoring/buckpal/BuckPalConfigurationProperties.kt` → 1 match
- [ ] `grep "var transferThreshold: Long = Long.MAX_VALUE" src/main/kotlin/io/reflectoring/buckpal/BuckPalConfigurationProperties.kt` → 1 match
- [ ] `grep "data class BuckPalConfigurationProperties" src/main/kotlin/io/reflectoring/buckpal/BuckPalConfigurationProperties.kt` → 1 match
- [ ] `JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home ./gradlew clean compileKotlin compileTestKotlin compileJava compileTestJava test check` → BUILD SUCCESSFUL, all tests pass
- [ ] `JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home ./gradlew test --tests "io.reflectoring.buckpal.BuckPalApplicationTests"` → PASS.
  **This is the load-bearing live wiring verification for Sprint 7.**
  `BuckPalApplicationTests.contextLoads()` is a `@SpringBootTest` that boots
  the full ApplicationContext — if `@SpringBootApplication`,
  `@EnableConfigurationProperties`, `@ConfigurationProperties` binding, the
  `@Bean` factory method, or `MoneyTransferProperties` construction fails,
  the context refresh fails and this test fails fast. Per the planner spec,
  `./gradlew bootRun` smoke is mentioned but is brittle in CI; the
  `@SpringBootTest`-driven context-load is the deterministic replacement
  and is the explicit Sprint 7 acceptance check for application wiring.
- [ ] `JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home ./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"` → PASS.
  This exercises the **full** Spring Boot context + H2 + HTTP path; if the
  `@Bean` wiring for `MoneyTransferProperties` regressed silently (e.g.
  `Money.of(0)` instead of `Money.of(Long.MAX_VALUE)` because the
  property-binding setter didn't fire), the threshold check inside
  `SendMoneyService` would still pass (small transfer amount), but the test
  serves as a redundant live probe.
- [ ] `JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home ./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` → PASS
  (ArchUnit green — package layout under `io.reflectoring.buckpal.**` preserved)
- [ ] kotlinc warnings on the 3 new files → 0
  (verified via `./gradlew clean compileKotlin --info | grep -E '^w:|warning:'` → 0 matches)
- [ ] `grep -R 'TODO\|FIXME\|XXX' src/main/kotlin/io/reflectoring/buckpal/BuckPalApplication.kt src/main/kotlin/io/reflectoring/buckpal/BuckPalConfiguration.kt src/main/kotlin/io/reflectoring/buckpal/BuckPalConfigurationProperties.kt` → 0 matches
- [ ] `git status` shows ONLY: 3 .java deletions in `src/main/java/io/reflectoring/buckpal/`,
  3 .kt additions in `src/main/kotlin/io/reflectoring/buckpal/`, plus the 3
  harness meta-files (contract, handoff, review). Any other modified path
  is an automatic FAIL per evaluator.md.

## Rubric coverage (per `criteria/kotlin-conversion.md`)

| Rubric criterion | How this contract verifies it |
|------------------|-------------------------------|
| Behavioral Correctness (35%, floor 9) | `./gradlew test check` green; targeted `BuckPalApplicationTests` (context load = wiring proof); targeted `SendMoneySystemTest` (full HTTP path through Spring context). |
| Idiomatic Kotlin (30%, floor 7) | Top-level `main` + `runApplication<T>(*args)`; `data class` for the config-property bag; class-literal `::class` syntax in `@EnableConfigurationProperties`; property access (`transferThreshold`) replaces Lombok getter; zero `!!`, `lateinit`, `@Autowired`, `Optional<>`; Lombok import removed (grep). |
| Architectural Integrity (20%, floor 9) | `DependencyRuleTests` green; package paths unchanged (3 files all live at `io.reflectoring.buckpal.*`); `@SpringBootApplication`, `@Configuration`, `@EnableConfigurationProperties`, `@ConfigurationProperties` annotations preserved exactly; bean topology unchanged (same `@Bean` factory method name, same return type). |
| Code Quality (15%, floor 7) | 0 kotlinc warnings on the 3 new files; 0 `TODO`/`FIXME`/`XXX`; file ↔ class name match; KDoc preserved with idiomatic `[Type]` cross-refs; imports alphabetized. |

## Idiomatic Kotlin commitments

1. **Top-level `main` function** with `runApplication<BuckPalApplication>(*args)`.
   No `companion object @JvmStatic fun main` — that would be a Java-shaped
   workaround. Spring Boot's Kotlin DSL exists precisely to make this clean.
2. **`data class` for `BuckPalConfigurationProperties`** — the Kotlin
   equivalent of Lombok's `@Data`. `var transferThreshold: Long = Long.MAX_VALUE`
   gives Spring the JavaBeans setter it needs and Kotlin call sites the
   property syntax they expect.
3. **`::class` class-literal** in `@EnableConfigurationProperties`
   (`BuckPalConfigurationProperties::class` instead of `.class` /
   `.java.class`).
4. **Single-expression `@Bean` function** with `=` and a multi-line
   constructor invocation — idiomatic Kotlin for a one-expression body.
5. **No `@ConstructorBinding`** — deliberately omitted to preserve
   JavaBeans-style binding semantics (spec risk register #8; Sprint 7
   spec notes). Adding it would change binding mechanics for zero benefit
   in a `var`-based data class.
6. **No `internal` modifier** on these 3 root types — the original Java
   had `public class` (and a `public @Bean` factory method). Public
   visibility is preserved verbatim; Spring's CGLIB proxying of
   `@Configuration` requires the class to be open (kotlin-spring
   handles `open` automatically) and visible.

## Out of scope

- Test conversion (`BuckPalApplicationTests.java`, `DependencyRuleTests.java`,
  `SendMoneySystemTest.java`, and the rest of `src/test/java/**`) — Sprint 8.
- Removing the Lombok `compileOnly` + `annotationProcessor` deps from
  `build.gradle` — Sprint 9 cleanup (after Sprint 8 also has no Lombok
  consumers in test sources).
- Removing the `apply plugin: 'java'` / `'java-library'` lines from
  `build.gradle` — Sprint 9 (still needed while test sources are Java).
- Optional move of `build.gradle` → `build.gradle.kts` — Sprint 9.
- Any change to `application.yml`, the `Account.kt` `Optional<AccountId>`
  shim, or any other file outside the 3 Java root classes.

## Risks specific to this sprint

1. **`@SpringBootApplication` package-scan root.** `@SpringBootApplication`'s
   default component-scan base package is the package of the annotated class
   (`io.reflectoring.buckpal`). The new `BuckPalApplication.kt` MUST stay
   in `io.reflectoring.buckpal` (not `io.reflectoring.buckpal.app` etc.) or
   component scan misses all the `@WebAdapter` / `@PersistenceAdapter` /
   `@UseCase` beans. The conversion preserves the package exactly.
2. **Top-level `main` ↔ Spring Boot Gradle plugin discovery.** The
   `spring-boot-gradle-plugin` (2.4.3) discovers the main class via the
   JAR manifest, looking for a class with a `public static void main`.
   Kotlin top-level `main` compiles to `BuckPalApplicationKt.main(...)`.
   Spring Boot 2.4.x supports this out of the box — `runApplication`'s
   reified type parameter still ensures the `SpringApplication` is rooted
   at `BuckPalApplication::class`, not `BuckPalApplicationKt`. The
   `BuckPalApplicationTests` `@SpringBootTest` resolves the configuration
   class via classpath scan from the test class's package, which is the
   same package — so the test is the deterministic proof of wiring.
3. **`@ConfigurationProperties` binding via `var`.** Boot 2.4.3 supports
   both constructor binding (with `@ConstructorBinding`) and JavaBeans
   binding (via setters). The Kotlin `var transferThreshold: Long`
   compiles to a JavaBean setter `setTransferThreshold(long)`. The
   binder discovers it via reflection — same path as the original
   `@Data`-generated setter. Verification: the
   `application.yml`-driven test profile has `buckpal.transferThreshold: 10000`
   — if binding silently fails, the field falls back to `Long.MAX_VALUE`
   and the `BuckPalConfiguration.moneyTransferProperties` `@Bean`
   constructs `Money.of(Long.MAX_VALUE)` instead of `Money.of(10000)`.
   `BuckPalApplicationTests.contextLoads` would still pass (no
   threshold-exceeded assertion), but the wiring is exercised end-to-end
   by `SendMoneySystemTest` (small transfer, threshold check is bypassed
   anyway given the default — listed as a sanity probe). Practical
   binding correctness is best proved by manual inspection that
   `var transferThreshold: Long` is exactly what Spring's binder
   handles — there is no automated assertion in the existing test
   suite that pins `transferThreshold == 10000` directly.
4. **`@Configuration` proxy mode.** Spring Boot defaults to CGLIB-proxied
   `@Configuration` (proxyBeanMethods = true). kotlin-spring opens the
   class so CGLIB can subclass it. No manual `open` keyword needed.
   The `@Bean` method must remain `public` (default in Kotlin) and
   non-final (kotlin-spring makes it `open`).
5. **KClass vs Class in `@EnableConfigurationProperties`.** The Spring
   annotation declares `Class<?>[] value()`. Kotlin's `::class` produces
   a `KClass<T>`; the Kotlin compiler auto-converts class literals in
   annotation arguments to `Class<T>` JVM literals. So
   `@EnableConfigurationProperties(BuckPalConfigurationProperties::class)`
   compiles to the equivalent of the Java `.class` form. No
   `.java` suffix needed (and `::class.java` would be wrong inside an
   annotation argument).
6. **`Long` ↔ `long` boxing.** Kotlin `Long` is `long` (primitive) in
   property accessors and `Long` (boxed) in nullable / generic contexts.
   `var transferThreshold: Long` (non-null) is a primitive field; the
   setter is `setTransferThreshold(long)` (primitive arg). Spring's
   binder copes with both. Default value `Long.MAX_VALUE` is a
   `kotlin.Long` constant compiling to `9223372036854775807L`. No
   boxing surprises.

## Implementation order

1. Read each of the 3 Java files again immediately before conversion.
2. Create the 3 `.kt` files **in dependency order**:
   1. `BuckPalConfigurationProperties.kt` (no deps on the other two).
   2. `BuckPalConfiguration.kt` (depends on `BuckPalConfigurationProperties`
      and on Sprint 4's `MoneyTransferProperties` + Sprint 2's `Money`).
   3. `BuckPalApplication.kt` (no deps on the other two).
3. Run `./gradlew compileKotlin` — must succeed before any deletion.
4. Delete the 3 `.java` files (`git rm` so deletion is staged later).
5. Run `./gradlew compileJava compileTestJava` — remaining Java
   consumers (only `src/test/java/**` is left) must still compile against
   the new Kotlin code. The test sources reference `BuckPalApplication`
   only by class literal (`@SpringBootTest` does its own scan from the
   test class's package) — see `BuckPalApplicationTests.java`.
6. Run `./gradlew test` — all tests must pass.
7. Targeted: `BuckPalApplicationTests` (context load).
8. Targeted: `SendMoneySystemTest` (full HTTP path).
9. Targeted: `DependencyRuleTests` (ArchUnit).
10. `./gradlew check` → green.
11. `./gradlew clean compileKotlin --info | grep -E '^w:|warning:'` → 0 matches.
12. `find src/main/java -name '*.java'` → expect empty.
13. Write `.claude/harness/workspace/handoffs/sprint-07-handoff.md`.
14. **DO NOT commit** — the orchestrator handles the commit after Phase B PASS.

## Self-check (Generator, before writing handoff)

- [ ] All 3 `.kt` files compile.
- [ ] All 3 `.java` files deleted.
- [ ] `find src/main/java -name '*.java'` → empty (THE Sprint 7 milestone).
- [ ] `find src/main/kotlin/io/reflectoring/buckpal -maxdepth 1 -name '*.kt'` → 3
- [ ] `./gradlew clean compileKotlin compileTestKotlin compileJava compileTestJava test check` → BUILD SUCCESSFUL, all tests pass
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.BuckPalApplicationTests"` → PASS
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"` → PASS
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` → PASS
- [ ] No `import lombok`, no `lateinit`, no `@Autowired`, no `Optional<`, no `!!`, no `@ConstructorBinding` in the 3 new files
- [ ] `@SpringBootApplication`, `@Configuration`, `@EnableConfigurationProperties`, `@ConfigurationProperties`, `@Bean` annotations preserved
- [ ] Class simple names preserved: `BuckPalApplication`, `BuckPalConfiguration`, `BuckPalConfigurationProperties`
- [ ] Package preserved: `io.reflectoring.buckpal`
- [ ] 0 kotlinc warnings on the 3 new files
- [ ] `git status` — only in-scope files (3 java deleted, 3 kt added, plus contract/handoff meta-files)
