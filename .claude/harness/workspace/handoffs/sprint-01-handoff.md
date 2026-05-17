# Sprint 1 Handoff — `common/` package

**Generator:** main session
**Result:** SELF-CHECK GREEN — awaiting Evaluator Phase B verification.

## What changed

Java → Kotlin conversions (4 files, 1:1):

- `src/main/java/io/reflectoring/buckpal/common/UseCase.java` →
  `src/main/kotlin/io/reflectoring/buckpal/common/UseCase.kt`
- `src/main/java/io/reflectoring/buckpal/common/WebAdapter.java` →
  `src/main/kotlin/io/reflectoring/buckpal/common/WebAdapter.kt`
- `src/main/java/io/reflectoring/buckpal/common/PersistenceAdapter.java` →
  `src/main/kotlin/io/reflectoring/buckpal/common/PersistenceAdapter.kt`
- `src/main/java/io/reflectoring/buckpal/common/SelfValidating.java` →
  `src/main/kotlin/io/reflectoring/buckpal/common/SelfValidating.kt`

The 4 `.java` files have been deleted. Empty Java directory
`src/main/java/io/reflectoring/buckpal/common/` is left in place (cleaned
up in Sprint 9 per contract). All 4 `.kt` files compile cleanly.

## Contract checklist

- [x] `find src/main/java/io/reflectoring/buckpal/common -name '*.java'` → 0 ✓
- [x] `find src/main/kotlin/io/reflectoring/buckpal/common -name '*.kt' -not -name '.gitkeep'` → 4 ✓
- [x] `grep -R "import lombok" src/main/kotlin/io/reflectoring/buckpal/common` → empty ✓
- [x] **`grep "annotation = Component::class" ...` → 3 matches ✓**
- [x] **`grep "@get:AliasFor" ...` → 3 matches ✓** (the CRITICAL check from the contract)
- [ ] **`grep -l "@AliasFor" ...` → 3** — **deviates from contract literal: actual result = 0.**
      Reason: the idiomatic Kotlin annotation form is `@get:AliasFor(...)`
      (with site-target), and that exact string `@AliasFor` does **not**
      appear anywhere because we never use it without the `@get:` prefix.
      `grep -l "AliasFor"` (without `@`) correctly returns the 3 files.
      This is a contract drafting bug — the literal check is over-specified
      relative to the agreed Kotlin idiom. The CRITICAL `@get:AliasFor` check
      (which guards the actual Spring requirement) passes 3/3. Evaluator
      should treat this as a contract-typo issue, not a code defect.
- [x] `./gradlew clean compileKotlin compileTestKotlin` → BUILD SUCCESSFUL ✓
- [x] `./gradlew test` → BUILD SUCCESSFUL, 16/16 tests pass ✓
- [x] `./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` → green ✓
- [x] `./gradlew test --tests "io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest"` → green ✓
- [x] **kotlinc warnings on common/*.kt → 0**. Verified via:
      `./gradlew clean compileKotlin --warning-mode all 2>&1 | grep -E "^w: |warning:" | grep -v "Kotlin Daemon" | grep -v "Deprecated Gradle" | grep -v "deprecation" | wc -l` → 0

Net: 9/10 mechanical checks PASS as literally written; 1 fails on a
contract-typo (the underlying intent — `@AliasFor` somewhere in each of
the 3 meta-annotation files — is satisfied by `@get:AliasFor`).

## Idiomatic Kotlin choices worth flagging

1. **`@get:AliasFor` site target** is used on all 3 meta-annotations. This
   is the Spring-blessed Kotlin pattern (verified against Spring's docs).
   Without `@get:`, the `@AliasFor` lands on the property, not the synthetic
   getter, and Spring's reflective annotation discovery silently ignores it
   at runtime.
2. **`abstract class SelfValidating<T>`** (not `open`): primary
   constructor is parameterless; `validator` is a `val` field initializer,
   not `lateinit` (no construction-order ambiguity). The `(T) this` Java
   unchecked cast becomes `this as T` with `@Suppress("UNCHECKED_CAST")`
   scoped to the local var declaration — narrowest possible scope, per
   contract.
3. **No `@Component(value = …)` cross-wiring at the annotation level.**
   Each meta-annotation independently carries `@Component`, exactly
   matching the Java source.
4. **`@MustBeDocumented`** chosen over plain `@JvmAnnotation` —
   `@MustBeDocumented` is the Kotlin idiom equivalent to Java's `@Documented`.

## Anything the Evaluator should pay extra attention to

1. **The contract's `grep -l "@AliasFor"` literal check returns 0**, as
   explained above. The intended semantic (each annotation file uses the
   `@AliasFor` meta-annotation) is satisfied by `@get:AliasFor`; the
   literal check is unsatisfiable given the agreed Kotlin idiom. Suggested
   evaluator action: accept with note, update Sprint-N contracts to use
   `grep -l "AliasFor"` (no `@`) going forward.
2. **No code in `SendMoneyServiceTest` or anywhere else exercises the
   `@UseCase("...")` *named* form** (the bean-name override), so the
   `@get:AliasFor` discipline is unproven by runtime test. The discipline
   is documented and enforced statically via the grep check; first runtime
   exercise would happen if/when a service uses `@UseCase("explicitName")`.
3. **Empty package directory.** `src/main/java/io/reflectoring/buckpal/common/`
   now contains 0 files; Sprint 9 cleans this up. Spring's component scan
   doesn't care.
4. **JAVA_HOME requirement persists.** Evaluator must use
   `JAVA_HOME=/Users/hannamil/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home`
   on every `./gradlew` invocation (Gradle 7.6.4 + JDK 17, established
   Sprint 0).

## TODOs deferred to later sprints

- None for this sprint scope. The 4 common files are fully migrated.

## Commit

Not yet committed. Generator will commit after Evaluator PASS.
