# Sprint 07 Handoff

## What changed

- Edited `build.gradle` — removed 4 now-unused `testImplementation` lines:
  - `org.junit.jupiter:junit-jupiter-engine:5.0.1`
  - `org.mockito:mockito-junit-jupiter:2.23.0`
  - `org.jetbrains.kotlin:kotlin-test`
  - `org.jetbrains.kotlin:kotlin-test-junit5`
- No source files added, converted, or deleted. No Java→Kotlin conversion in
  this sprint (build-script cleanup only, per spec & contract).

## Contract checklist

### Behavioral

- [x] `./gradlew clean test` → BUILD SUCCESSFUL in 14s, exit 0.
- [x] Aggregate JUnit XML leaf count = 16 across 8 suites:
  `find build/test-results/test -name 'TEST-*.xml' -exec grep -h '<testcase ' {} \; | wc -l` → **16**.
  Suites present: `AccountTest`, `ActivityWindowTest`, `SendMoneyServiceTest`,
  `SendMoneyControllerTest`, `AccountPersistenceAdapterTest`,
  `DependencyRuleTests`, `BuckPalApplicationTests`, `SendMoneySystemTest`.
- [x] `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"` →
  BUILD SUCCESSFUL, exit 0.

### Architectural

- [x] `./gradlew check` → BUILD SUCCESSFUL, exit 0 (ArchUnit
  `DependencyRuleTests` green; `archunit:0.16.0` still on test classpath).
- [x] `git diff --name-only HEAD -- src/main/` → empty.
- [x] `git diff --name-only HEAD -- src/test/` → empty.

### Code Quality — negative greps (expected 0 matches)

- [x] `grep -c "junit-jupiter-engine" build.gradle` → **0**.
- [x] `grep -c "mockito-junit-jupiter" build.gradle` → **0**.
- [x] `grep -cF "'org.jetbrains.kotlin:kotlin-test'" build.gradle` → **0**.
- [x] `grep -cF "'org.jetbrains.kotlin:kotlin-test-junit5'" build.gradle` → **0**.

### Code Quality — positive greps

- [x] `kotest-runner-junit5:5.5.5` → 1.
- [x] `kotest-assertions-core:5.5.5` → 1.
- [x] `kotest-extensions-spring:1.1.3` → 1.
- [x] `io.mockk:mockk:1.13.8` → 1.
- [x] `com.ninja-squad:springmockk:3.1.2` → 1.
- [x] `kotlinx-coroutines-core(-jvm)?:1\.6\.4` → 2 (core + core-jvm).
- [x] `kotlinx-coroutines-test(-jvm)?:1\.6\.4` → 2 (test + test-jvm).
- [x] `junit-platform-launcher:1.4.2` → 1.
- [x] `com.tngtech.archunit:archunit:0.16.0` → 1.
- [x] `spring-boot-starter-test` → 1.
- [x] `useJUnitPlatform()` → 1.

### Scope

- [x] `git diff --name-only HEAD` lists exactly one in-scope tracked file:
  `build.gradle`. (Also present: `.claude/harness/workspace/logs/run-log.md` —
  harness orchestration state under `.claude/harness/workspace/`, excluded
  from the spec's source-tree scope check per the contract.)

## Idiomatic Kotlin choices worth flagging

N/A — build script (Groovy DSL) only. No Kotlin source touched.

## Anything the Evaluator should pay extra attention to

- The `spring-boot-starter-test` block retains only its existing
  `exclude group: 'junit'` (JUnit 4); per contract, **no additional
  `exclude` for `junit-jupiter`** was added. The transitive Jupiter engine
  is benign — zero `@Test`-annotated methods exist after Sprints 01–06, so
  the Jupiter engine discovers nothing and Kotest's runner handles all
  leaves. This is the documented decision in the contract's "Out of scope".
- Diff is minimal: 4 line deletions, no reformatting, no surrounding line
  shuffles.

## TODOs deferred to later sprints

None — this is the final sprint of the Kotest + MockK migration. The build
script now represents the final desired state described in the product spec.

## Commit

Proposed one-line subject:

`feat(kotlin): sprint 7 — strip obsolete test dependencies (junit-jupiter-engine, mockito-junit-jupiter, kotlin-test, kotlin-test-junit5)`

(sha pending — orchestrator owns the commit.)
