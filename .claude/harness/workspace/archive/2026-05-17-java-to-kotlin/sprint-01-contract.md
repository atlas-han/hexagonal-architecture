# Sprint 1 Contract — `common/` package

STATUS: AGREED
**Generator:** main session
**Sprint goal (from spec):** Convert the 4-class `common` package — pure-
annotation and tiny abstract class — establishing the basic conversion
pattern.

## Files in scope

Production:
- `src/main/java/io/reflectoring/buckpal/common/UseCase.java` → delete
- `src/main/java/io/reflectoring/buckpal/common/WebAdapter.java` → delete
- `src/main/java/io/reflectoring/buckpal/common/PersistenceAdapter.java` → delete
- `src/main/java/io/reflectoring/buckpal/common/SelfValidating.java` → delete
- `src/main/kotlin/io/reflectoring/buckpal/common/UseCase.kt` → create
- `src/main/kotlin/io/reflectoring/buckpal/common/WebAdapter.kt` → create
- `src/main/kotlin/io/reflectoring/buckpal/common/PersistenceAdapter.kt` → create
- `src/main/kotlin/io/reflectoring/buckpal/common/SelfValidating.kt` → create

Nothing under `src/test/` or any other production package may be edited.

## Conversion targets

| Java file | Kotlin equivalent | Type |
|-----------|-------------------|------|
| `UseCase.java` | `UseCase.kt` | `annotation class` with `@AliasFor` on property getter |
| `WebAdapter.java` | `WebAdapter.kt` | `annotation class` with `@AliasFor` on property getter |
| `PersistenceAdapter.java` | `PersistenceAdapter.kt` | `annotation class` with `@AliasFor` on property getter |
| `SelfValidating.java` | `SelfValidating.kt` | `abstract class SelfValidating<T>` |

## Acceptance checks

- [ ] `find src/main/java/io/reflectoring/buckpal/common -name '*.java'` → empty (0 results)
- [ ] `find src/main/kotlin/io/reflectoring/buckpal/common -name '*.kt' -not -name '.gitkeep'` → 4 results
- [ ] `grep -R "import lombok" src/main/kotlin/io/reflectoring/buckpal/common` → empty
- [ ] `grep -l "@AliasFor" src/main/kotlin/io/reflectoring/buckpal/common/*.kt | wc -l` → 3 (UseCase, WebAdapter, PersistenceAdapter all use the meta-annotation alias)
- [ ] `grep "annotation = Component::class" src/main/kotlin/io/reflectoring/buckpal/common/*.kt | wc -l` → 3
- [ ] `grep "@get:AliasFor" src/main/kotlin/io/reflectoring/buckpal/common/*.kt | wc -l` → 3 (CRITICAL — without `@get:` site target, Spring won't see the alias on Kotlin annotation properties)
- [ ] `JAVA_HOME=... ./gradlew clean compileKotlin compileTestKotlin` → BUILD SUCCESSFUL
- [ ] `JAVA_HOME=... ./gradlew test` → BUILD SUCCESSFUL, 16/16 tests pass
- [ ] `JAVA_HOME=... ./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` → green (annotation FQNs preserved)
- [ ] `JAVA_HOME=... ./gradlew test --tests "io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest"` → green (this test depends on `@UseCase` resolving correctly via Spring)
- [ ] Kotlin compiler warnings on the 4 new files (visible in build output) → 0
<!-- EVALUATOR: "warnings → 0" is not mechanically verifiable as written — kotlinc/Gradle does not emit a literal "0 warnings" summary line. Replace with a concrete check, e.g.:
     `JAVA_HOME=... ./gradlew clean compileKotlin 2>&1 | grep -E "^w: |warning:" | grep -v "Kotlin Daemon" | wc -l` → 0
     (or `--warning-mode all` with grep for "^w: "). The Generator must use whichever invocation actually surfaces kotlinc warnings in this build, and capture the command in the handoff. Otherwise this acceptance check is a vibe check and will be skipped at review time. -->

## Idiomatic Kotlin commitments

1. **Annotation classes use property syntax**, not Java-style methods.
   Kotlin annotation params live on properties; Spring's `@AliasFor` must
   be site-targeted to `@get:` so it lands on the synthetic getter, not
   the parameter. Without this, the meta-annotation alias is silently
   ignored at runtime.
2. **`@Component` is applied** to each meta-annotation, identically to the
   Java source.
3. **`@Target(AnnotationTarget.CLASS)`** to match Java's
   `@Target({ElementType.TYPE})`.
4. **`@Retention(AnnotationRetention.RUNTIME)`** matches `RUNTIME`.
5. **`@MustBeDocumented`** matches `@Documented`.
6. **`SelfValidating<T>`**: `abstract class` (not `open class` — Kotlin's
   default is `final`; only `abstract` is needed for inheritance here).
   The `(T) this` unchecked cast becomes `this as T` with
   `@Suppress("UNCHECKED_CAST")`. Validator is `val` (immutable),
   initialized in primary-constructor body or as field initializer.
   `validateSelf` keeps `protected` visibility.

## Out of scope

- Any package outside `common`.
- Removing the `@MustBeDocumented` requirement / changing annotation
  semantics.
- Refactoring `SelfValidating` to avoid the unchecked cast.

## Implementation order

1. Create the 4 `.kt` files under `src/main/kotlin/io/reflectoring/buckpal/common/`.
2. Run `./gradlew compileKotlin` to make sure they compile.
3. Delete the 4 `.java` files under `src/main/java/io/reflectoring/buckpal/common/`.
   Note: the parent directory `src/main/java/io/reflectoring/buckpal/common`
   may become empty; leave the empty dir in place (Spring's classpath scan
   doesn't care). Sprint 9 will clean up empties.
4. Run the full self-check: `./gradlew clean compileKotlin compileTestKotlin test`.
5. Run ArchUnit specifically:
   `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"`.
6. Run `SendMoneyServiceTest` to verify `@UseCase` still resolves Spring
   beans correctly.
7. If green, write handoff. If red, fix and re-run.

## Risks specific to this sprint

1. **`@AliasFor` site-targeting.** If forgotten, `@UseCase(value =
   "myService")` will not set the Spring bean name. ArchUnit may still
   pass (it only cares about class location), but a runtime test that
   relies on bean naming would fail. Sprint 4 brings such tests under
   test once services are in Kotlin. Get it right now.
2. **`@Suppress("UNCHECKED_CAST")` placement.** Must be on the function
   or the cast expression; placing it on the class is too broad.
3. **No nullable Validator.** The original Java keeps Validator as a
   private field initialized in the constructor. Kotlin equivalent is
   a `val` with field initializer — `lateinit` is **not** appropriate
   here (no construction order ambiguity).
