STATUS: AGREED

# Sprint 07 Contract — Strip obsolete test dependencies and final verification

## Sprint goal (verbatim from spec)

> Now that every test class is on Kotest + MockK, remove the now-unused legacy
> test dependencies and confirm the suite is still green. Leaves the build
> script representing the final desired state.

## Files in scope

Only this **one** build script may be edited in Sprint 07. Anything outside
this list — production code, any `src/main/**` or `src/test/**` source file,
Gradle wrapper, settings, or `gradle.properties` — is off-limits.

- `build.gradle`

## Conversion targets

This is a build-script cleanup sprint; **no Java → Kotlin conversion** occurs.
The single edit is to remove four `testImplementation` lines (verified to be
present in the current `build.gradle`):

- L45 `testImplementation 'org.junit.jupiter:junit-jupiter-engine:5.0.1'` → **remove**
- L46 `testImplementation 'org.mockito:mockito-junit-jupiter:2.23.0'` → **remove**
- L50 `testImplementation 'org.jetbrains.kotlin:kotlin-test'` → **remove**
- L51 `testImplementation 'org.jetbrains.kotlin:kotlin-test-junit5'` → **remove**

The following lines explicitly **remain unchanged**:

- `testImplementation('org.springframework.boot:spring-boot-starter-test') { exclude group: 'junit' }`
  (the `exclude group: 'junit'` keeps JUnit 4 out; **no additional `exclude` for
  `junit-jupiter` is added** — Kotest tolerates Jupiter engine being on the
  classpath, and the spec does not ask for it.)
- `testImplementation 'com.tngtech.archunit:archunit:0.16.0'`
- `testImplementation 'org.junit.platform:junit-platform-launcher:1.4.2'`
  (kept per spec — "some tooling expects it"; Gradle's
  `useJUnitPlatform()` uses it to load engines)
- `testImplementation 'com.h2database:h2'`
- `testImplementation 'io.kotest:kotest-runner-junit5:5.5.5'`
- `testImplementation 'io.kotest:kotest-assertions-core:5.5.5'`
- `testImplementation 'io.kotest.extensions:kotest-extensions-spring:1.1.3'`
- `testImplementation 'io.mockk:mockk:1.13.8'`
- `testImplementation 'com.ninja-squad:springmockk:3.1.2'`
- `testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4'`
- `testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.4'`
- `testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4'`
- `testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.6.4'`
- `runtimeOnly 'com.h2database:h2'`
- `test { useJUnitPlatform() }`
- All `plugins { ... }` entries, `compileKotlin` / `compileTestKotlin` blocks,
  `repositories`, and `implementation` lines.

## Idiomatic Kotlin commitments

N/A — this sprint edits only the Gradle build script. Idiomatic-Kotlin
considerations do not apply at the test-DSL level for `build.gradle` (Groovy).

## Acceptance checks

### Behavioral

- [ ] `./gradlew clean test` exits 0.
- [ ] After the cleanup, the JUnit XML reports under
  `build/test-results/test/` aggregate to **exactly 16 leaves** across the
  8 suites (matching the Sprint 05/06 baseline:
  `AccountTest=1`, `ActivityWindowTest=2`, `SendMoneyServiceTest=2`,
  `SendMoneyControllerTest=4`, `AccountPersistenceAdapterTest=3`,
  `DependencyRuleTests=1`, `BuckPalApplicationTests=2`,
  `SendMoneySystemTest=1`). Verify with:
  `find build/test-results/test -name 'TEST-*.xml' -exec grep -h '<testcase ' {} \; | wc -l`
  → output **16**.
- [ ] `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"`
  exits 0 (Spring boots, system test still passes its single leaf).

### Idiomatic

- N/A (build script only — no Kotlin source touched).

### Architectural

- [ ] `./gradlew check` exits 0 (ArchUnit `DependencyRuleTests` still green —
  `archunit:0.16.0` remains on the test classpath).
- [ ] Production sources unchanged:
  `git diff --name-only HEAD -- src/main/` → empty.
- [ ] Test sources unchanged:
  `git diff --name-only HEAD -- src/test/` → empty.

### Code Quality

Negative greps on `build.gradle` (must return **0 matches** — `grep -c`
prints `0`, exit code 1 is acceptable for these "must be absent" checks):

- [ ] `grep -n "junit-jupiter-engine" build.gradle` → no matches.
- [ ] `grep -n "mockito-junit-jupiter" build.gradle` → no matches.
- [ ] `grep -nF "'org.jetbrains.kotlin:kotlin-test'" build.gradle` → no matches.
- [ ] `grep -nF "'org.jetbrains.kotlin:kotlin-test-junit5'" build.gradle` → no matches.

Positive greps on `build.gradle` (must each return **≥1 match**):

- [ ] `grep -n "kotest-runner-junit5:5.5.5" build.gradle` → 1 match.
- [ ] `grep -n "kotest-assertions-core:5.5.5" build.gradle` → 1 match.
- [ ] `grep -n "kotest-extensions-spring:1.1.3" build.gradle` → 1 match.
- [ ] `grep -n "io.mockk:mockk:1.13.8" build.gradle` → 1 match.
- [ ] `grep -n "com.ninja-squad:springmockk:3.1.2" build.gradle` → 1 match.
- [ ] `grep -nE "kotlinx-coroutines-core(-jvm)?:1\.6\.4" build.gradle` → 2 matches
  (one `core`, one `core-jvm`).
- [ ] `grep -nE "kotlinx-coroutines-test(-jvm)?:1\.6\.4" build.gradle` → 2 matches
  (one `test`, one `test-jvm`).
- [ ] `grep -n "junit-platform-launcher:1.4.2" build.gradle` → 1 match.
- [ ] `grep -n "com.tngtech.archunit:archunit:0.16.0" build.gradle` → 1 match.
- [ ] `grep -n "spring-boot-starter-test" build.gradle` → 1 match.
- [ ] `grep -nF "useJUnitPlatform()" build.gradle` → 1 match.

### Scope

- [ ] `git diff --name-only HEAD` lists **exactly one file**: `build.gradle`.
  No other tracked file is modified. (Files under
  `.claude/harness/workspace/` are not tracked as production output and are
  excluded from this scope check — the spec's scope clause is about the build
  / source tree.)

## Out of scope (copied from spec, plus drafting notes)

- Kotlin / Spring Boot version bumps.
- Conversion to `build.gradle.kts`.
- Any change to `src/test/**` or `src/main/**`.
- Adding a `configurations.all { exclude group: 'org.junit.jupiter', module: 'junit-jupiter-engine' }`
  block to strip the engine that `spring-boot-starter-test` transitively
  pulls in. **Rationale:** Kotest's `kotest-runner-junit5` is itself a
  JUnit Platform engine; Jupiter engine being present transitively is benign
  (zero `@Test`-annotated methods remain after Sprints 01–06, so the Jupiter
  engine simply discovers nothing). The spec only requires removing the
  *explicit* `testImplementation` declarations, not blocking the transitive.
- Bumping `junit-platform-launcher` to a newer version aligned with the
  current JUnit BOM. Spec explicitly says it "remains unchanged".
- Reformatting the `build.gradle` (trailing blank lines, alignment, etc.).
  Edits are strictly line removals to keep the diff minimal.
