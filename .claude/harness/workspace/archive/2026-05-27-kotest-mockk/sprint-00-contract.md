STATUS: AGREED

# Sprint 00 Contract — Build config: introduce Kotest + MockK alongside the existing stack

## Sprint goal

Add Kotest (JUnit5 runner + core assertions + Spring extension) and MockK
(core + springmockk) as test dependencies, **without removing** any existing
test dependency. The repository continues to compile every existing test class
unchanged and `./gradlew test` stays green.

## Files in scope

- `build.gradle`

Anything outside this single file is off-limits for Sprint 00. In particular,
no file under `src/main/**` or `src/test/**` may be edited, created, or deleted.

## Goal (verbatim from spec)

> Add Kotest (JUnit5 runner + core assertions + Spring extension) and MockK
> (core + springmockk) as test dependencies, **without removing** any existing
> test dependency. The repository continues to compile every existing test
> class unchanged.

## Hard exit criteria (verbatim from spec)

- `build.gradle` declares `io.kotest:kotest-runner-junit5`,
  `io.kotest:kotest-assertions-core`,
  `io.kotest:kotest-extensions-spring`,
  `io.mockk:mockk`, and `com.ninja-squad:springmockk` at versions
  compatible with Kotlin 1.6.21 / Spring Boot 2.4.3
  (Kotest 5.5.x line, MockK 1.13.x, springmockk 3.x).
- `junit-jupiter-engine`, `mockito-junit-jupiter`, `kotlin-test`,
  `kotlin-test-junit5`, `archunit`, `junit-platform-launcher`, `h2`, and the
  `spring-boot-starter-test` declaration are **unchanged**.
- `test { useJUnitPlatform() }` block is unchanged.
- `./gradlew dependencies --configuration testRuntimeClasspath | grep -E "(kotest|mockk)"` lists the new artifacts.
- `./gradlew test` exits 0; no test classes are edited in this sprint.

## Out of scope (verbatim from spec, plus additions)

From spec:
- any change to `src/test/**`
- removing any existing test dependency
- switching the build script to `.kts`
- bumping Kotlin / Spring Boot versions
- touching `compileKotlin` options

Generator-added:
- editing `src/main/**` (including `src/main/kotlin/**`)
- editing `compileTestKotlin` options block
- editing `repositories`, `plugins`, `group`, `version`, or any non-`dependencies`
  block other than what the spec already permits
- introducing Kotest plugin `io.kotest:kotest-framework-multiplatform-plugin-gradle`
  (we only need the JUnit5 runner — no plugin install required)
- adding `kotest-property`, `kotest-assertions-json`, or any other Kotest
  add-on module not listed in the spec
- adding any new `repositories { ... }` entry (Maven Central already covers all
  artifacts in scope)

---

## Generator-proposed concrete changes

The following changes will be applied to `build.gradle` (and only `build.gradle`).

### Version pins

The Generator will pin these exact versions, all compatible with Kotlin 1.6.21
and Spring Boot 2.4.3:

| Artifact                                    | Version |
|---------------------------------------------|---------|
| `io.kotest:kotest-runner-junit5`            | `5.5.5` |
| `io.kotest:kotest-assertions-core`          | `5.5.5` |
| `io.kotest:kotest-extensions-spring`        | `1.1.3` |
| `io.mockk:mockk`                            | `1.13.8` |
| `com.ninja-squad:springmockk`               | `3.1.2` |

Rationale (informational only — Generator will not add comments to
`build.gradle` for it):

- **Kotest 5.5.x** is the last 5.x line that still ships a Kotlin-1.6-compatible
  runtime; 5.6+ raises the Kotlin floor.
- **`kotest-extensions-spring` 1.1.3** is the Spring extension version paired
  with the Kotest 5.5.x line (the extension moved out of the main `kotest-*`
  coordinate group in 5.x).
- **MockK 1.13.8** supports Kotlin 1.6 and final-class mocking via the
  transitive `mockk-agent-jvm` (covers Risk Register item #4).
- **springmockk 3.1.2** is the 3.x line, which targets Spring Boot 2.4.x
  (covers Risk Register item #3).

### Where each dependency goes in `build.gradle`

All five new dependencies are added inside the existing top-level
`dependencies { ... }` block, in the `testImplementation` section, immediately
**after** the existing line
`testImplementation 'org.jetbrains.kotlin:kotlin-test-junit5'` and **before**
the `runtimeOnly 'com.h2database:h2'` line. They are added as five contiguous
lines, in this order:

```
testImplementation 'io.kotest:kotest-runner-junit5:5.5.5'
testImplementation 'io.kotest:kotest-assertions-core:5.5.5'
testImplementation 'io.kotest:kotest-extensions-spring:1.1.3'
testImplementation 'io.mockk:mockk:1.13.8'
testImplementation 'com.ninja-squad:springmockk:3.1.2'
```

### Dependencies kept **unchanged** (must remain byte-identical)

The Generator will not touch any of these lines:

- `testImplementation('org.springframework.boot:spring-boot-starter-test') { exclude group: 'junit' }`
- `testImplementation 'org.junit.jupiter:junit-jupiter-engine:5.0.1'`
- `testImplementation 'org.mockito:mockito-junit-jupiter:2.23.0'`
- `testImplementation 'com.tngtech.archunit:archunit:0.16.0'`
- `testImplementation 'org.junit.platform:junit-platform-launcher:1.4.2'`
- `testImplementation 'com.h2database:h2'`
- `testImplementation 'org.jetbrains.kotlin:kotlin-test'`
- `testImplementation 'org.jetbrains.kotlin:kotlin-test-junit5'`
- `runtimeOnly 'com.h2database:h2'`
- the `test { useJUnitPlatform() }` block
- the `compileKotlin { ... }` and `compileTestKotlin { ... }` blocks
- the `plugins { ... }`, `repositories { ... }`, `group`, `version`, and
  `apply plugin: 'io.spring.dependency-management'` lines

### Test runner / engine collision handling

`kotest-runner-junit5` registers a JUnit Platform `TestEngine`. The existing
`junit-jupiter-engine:5.0.1` registers its own engine. **Both engines are
intentionally left active for Sprint 00 through Sprint 06.** This is mandated
by the spec (Risk Register #5) and is the reason `useJUnitPlatform()` stays
untouched: the platform discovers and runs both engines concurrently.

Concrete handling for Sprint 00:

- Do **not** add `excludeEngines` / `includeEngines` filters to the `test`
  block. Both engines must run.
- Do **not** exclude transitive `junit-platform-engine` from any artifact.
  Kotest brings its own `junit-platform-engine` transitively at a version
  compatible with the existing `junit-platform-launcher:1.4.2`; the resolver
  will pick the higher version (an upgrade only on the platform side, which
  is API-stable across the 1.x line and does not affect existing JUnit 5
  test classes).
- Do **not** add `mockk-agent-jvm` explicitly — it arrives transitively via
  `io.mockk:mockk:1.13.8`. (Spec Risk Register #4 only requires the agent if
  a final-class mocking failure surfaces, which cannot happen in Sprint 00
  since no test code is edited.)
- If `./gradlew test` reports duplicate test execution (the same `@Test`
  picked up by both engines), that is a real defect — Generator stops and
  writes `needs input:` rather than masking it. (Not expected: Jupiter only
  scans `@Test`-annotated methods, Kotest only scans `Spec` subclasses;
  current test tree has no overlap.)

### Verification commands the Generator will run before handoff

In order, from the worktree root:

1. `./gradlew --no-daemon compileKotlin compileTestKotlin`
   → expect `BUILD SUCCESSFUL`.
2. `./gradlew --no-daemon dependencies --configuration testRuntimeClasspath | grep -E "(kotest|mockk|springmockk)"`
   → expect lines for `kotest-runner-junit5:5.5.5`,
   `kotest-assertions-core:5.5.5`, `kotest-extensions-spring:1.1.3`,
   `mockk:1.13.8`, and `springmockk:3.1.2` (each may appear more than once
   via transitive paths — at least one occurrence per artifact is required).
3. `./gradlew --no-daemon dependencies --configuration testRuntimeClasspath | grep -E "(junit-jupiter-engine|mockito-junit-jupiter|kotlin-test|archunit|junit-platform-launcher|h2)"`
   → expect every previously-present artifact still present (negative-removal
   evidence for the "unchanged" exit criterion).
4. `./gradlew --no-daemon test`
   → expect `BUILD SUCCESSFUL` and the same test count as the pre-sprint
   baseline (no test classes were edited, so the count must be identical).
5. `git diff --stat` from the worktree root
   → expect exactly one line: `build.gradle | <N> +`. No deletions, no other
   files touched.

If step 4 fails, the Generator will not hand off; it will diagnose
(typically a version-pin issue) and rerun.

## Acceptance checks (mechanically verifiable by Evaluator)

- [ ] `grep -E "io.kotest:kotest-runner-junit5:5\\.5\\.5" build.gradle` → matches one line
- [ ] `grep -E "io.kotest:kotest-assertions-core:5\\.5\\.5" build.gradle` → matches one line
- [ ] `grep -E "io.kotest:kotest-extensions-spring:1\\.1\\.3" build.gradle` → matches one line
- [ ] `grep -E "io.mockk:mockk:1\\.13\\.8" build.gradle` → matches one line
- [ ] `grep -E "com.ninja-squad:springmockk:3\\.1\\.2" build.gradle` → matches one line
- [ ] `grep -E "junit-jupiter-engine:5\\.0\\.1" build.gradle` → matches one line (unchanged)
- [ ] `grep -E "mockito-junit-jupiter:2\\.23\\.0" build.gradle` → matches one line (unchanged)
- [ ] `grep -E "kotlin-test(-junit5)?'" build.gradle` → matches two lines (both kotlin-test variants present)
- [ ] `grep -E "archunit:0\\.16\\.0" build.gradle` → matches one line (unchanged)
- [ ] `grep -E "junit-platform-launcher:1\\.4\\.2" build.gradle` → matches one line (unchanged)
- [ ] `grep -E "useJUnitPlatform\\(\\)" build.gradle` → matches one line (unchanged)
- [ ] `git diff --name-only HEAD` lists only `build.gradle` → no other files touched
- [ ] `./gradlew dependencies --configuration testRuntimeClasspath | grep -E "(kotest|mockk)"` → non-empty output listing all five new artifacts
- [ ] `./gradlew test` → exits 0
