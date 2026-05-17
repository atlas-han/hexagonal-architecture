STATUS: AGREED

# Sprint 0 Contract — Build configuration

**Generator:** main session
**Sprint goal (from spec):** Introduce Kotlin tooling so subsequent sprints
can land `.kt` files. Keep Java compilation working side-by-side.

## Files in scope

- `build.gradle` (modify)
- `src/main/kotlin/` (create empty directory — placeholder for source set)
- `src/test/kotlin/` (create empty directory — placeholder for source set)

Nothing else may be touched in this sprint. No `.java` files renamed,
moved, or deleted.

## Conversion targets

| Java file | Kotlin equivalent | Type |
|-----------|-------------------|------|
| *(none)*  | *(none)*          | *(this sprint adds tooling only — no .java → .kt conversion)* |

## Acceptance checks

- [ ] `grep -E "org.jetbrains.kotlin.jvm" build.gradle` → at least 1 match (Kotlin JVM plugin applied)
- [ ] `grep -E "org.jetbrains.kotlin.plugin.spring" build.gradle` → at least 1 match (kotlin-spring plugin for `open` on Spring beans)
- [ ] `grep -E "org.jetbrains.kotlin.plugin.jpa" build.gradle` → at least 1 match (kotlin-jpa plugin for JPA no-arg ctors)
- [ ] `grep -E "kotlin-stdlib" build.gradle` → at least 1 match
- [ ] `grep -E "kotlin-reflect" build.gradle` → at least 1 match
- [ ] `grep -E "jackson-module-kotlin" build.gradle` → at least 1 match
- [ ] `grep -E "kotlin-test" build.gradle` → at least 1 match (test dep present)
- [ ] `grep -E "jvmTarget" build.gradle` → at least 1 match, value is `'11'`
- [ ] `./gradlew clean compileKotlin compileTestKotlin` → BUILD SUCCESSFUL (kotlin compile is a no-op since no .kt sources yet, but the task must exist)
- [ ] `./gradlew test` → BUILD SUCCESSFUL, all existing tests pass (count must match pre-sprint baseline)
- [ ] `git diff --stat HEAD~1 -- src/main/java src/test/java` → empty (no Java source touched)
- [ ] `find src -name '*.java' | wc -l` → unchanged from before sprint (29 + 14 = 43)
- [ ] `find src/main/kotlin src/test/kotlin -type d 2>/dev/null` → both directories exist (may be empty or contain only `.gitkeep`)

## Idiomatic Kotlin commitments

This sprint touches build tooling only, so Kotlin idioms are N/A in code.
However:

- Plugin version pinned to **Kotlin 1.6.21** — the highest 1.6.x release;
  compatible with Spring Boot 2.4.3 (which expects Kotlin 1.4.x but works
  with 1.5/1.6 without trouble). Choosing 1.6 over 1.5 gets us
  `JvmInline value class` stable, `kotlinx.serialization` improvements, and
  Java 17 target support (we use 11 here). 1.7 introduces stricter
  inference rules that might surprise the migration and isn't necessary.
- Gradle Kotlin plugin DSL block style: `apply plugin: 'kotlin'` Groovy
  syntax (consistent with existing `apply plugin: 'java'`), not the
  `plugins { kotlin("jvm") }` DSL — keeps the file Groovy-only for now.
  Switching to `build.gradle.kts` is deferred to Sprint 9.
- `compileKotlin` and `compileTestKotlin` both get `kotlinOptions.jvmTarget
  = '11'` to match `compileJava.sourceCompatibility = 11`.

## Out of scope

- Any `.java` → `.kt` conversion (Sprints 1–8).
- Removing Lombok dependency (Sprint 9).
- Switching to `build.gradle.kts` (Sprint 9, optional).
- Adding MockK (deferred unless a test sprint demands it).
- Touching `gradle/wrapper/gradle-wrapper.properties` unless Kotlin plugin
  rejects the current Gradle version (current Gradle is whatever the
  wrapper provides; verify in implementation).

## Implementation order

1. Read current `build.gradle`.
2. Add Kotlin plugin block (Groovy `apply plugin:` style).
3. Add Kotlin deps (`stdlib`, `reflect`, `jackson-module-kotlin`,
   `kotlin-test`, `kotlin-test-junit5`).
4. Add `compileKotlin { kotlinOptions { jvmTarget = '11' } }` and the same
   for `compileTestKotlin`.
5. Create empty `src/main/kotlin/` and `src/test/kotlin/` directories with
   `.gitkeep` so git tracks them.
6. Run `./gradlew --refresh-dependencies clean compileKotlin compileTestKotlin test`.
7. If green, write handoff. If red, fix and re-run.
