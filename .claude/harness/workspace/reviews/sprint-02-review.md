STATUS: PASS

GITLEAKS_VIOLATIONS: SKIPPED
SOLID_VIOLATIONS: NO

# Sprint 02 Review — ActivityTimestamp value class

WEIGHTED SCORE: 9.4 / 10

## Security Scan

GITLEAKS_VIOLATIONS: SKIPPED

`command -v gitleaks` returned non-zero on this Linux host
(`GITLEAKS_NOT_INSTALLED`). The evaluator playbook's install step is
macOS/Homebrew-specific, so the scan is skipped per the agent's documented
escape hatch. No automatic FAIL from this.

## Mandatory commands

### Check 1 — `ActivityTimestamp.kt` exists

```
$ test -f src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt; echo "exit=$?"
exit=0
```
PASS.

### Check 2 — `@JvmInline` + `value class ActivityTimestamp` + `val value: LocalDateTime`

```
$ grep -Eq '@JvmInline' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt \
    && grep -Eq 'value[[:space:]]+class[[:space:]]+ActivityTimestamp' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt \
    && grep -Eq 'val[[:space:]]+value[[:space:]]*:[[:space:]]*LocalDateTime' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt; echo "exit=$?"
exit=0
```
PASS. Confirmed by reading the file: it is exactly
`@JvmInline value class ActivityTimestamp(val value: LocalDateTime)` in the
`io.reflectoring.buckpal.account.domain` package.

### Check 2b — `companion object` + `fun now(): ActivityTimestamp`

```
$ grep -q 'companion[[:space:]]*object' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt \
    && grep -Eq 'fun[[:space:]]+now\s*\(\s*\)[[:space:]]*:[[:space:]]*ActivityTimestamp' src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt; echo "exit=$?"
exit=0
```
PASS. Body is
`companion object { fun now(): ActivityTimestamp = ActivityTimestamp(LocalDateTime.now()) }`.

### Check 3 — `Activity.timestamp: ActivityTimestamp`

```
$ grep -Eq 'timestamp[[:space:]]*:[[:space:]]*ActivityTimestamp' \
    src/main/kotlin/io/reflectoring/buckpal/account/domain/Activity.kt; echo "exit=$?"
exit=0
```
PASS. Direct file read confirms both the primary `data class` parameter
(`val timestamp: ActivityTimestamp`) and the secondary constructor parameter
carry the VO type. The `java.time.LocalDateTime` import was removed from this
file (no longer referenced).

### Check 4 — `ActivityWindow.getStartTimestamp` / `getEndTimestamp` return `ActivityTimestamp`

```
$ grep -E 'fun (getStartTimestamp|getEndTimestamp)\s*\([^)]*\)\s*:\s*ActivityTimestamp' \
    src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityWindow.kt; echo "exit=$?"
    fun getStartTimestamp(): ActivityTimestamp =
    fun getEndTimestamp(): ActivityTimestamp =
exit=0
```
PASS. Two matching lines — exactly what the contract demands.

### Check 5 — no raw `LocalDateTime.now()` in `account/domain/`

```
$ grep -RnE 'LocalDateTime\.now\s*\(\s*\)' \
    src/main/kotlin/io/reflectoring/buckpal/account/domain/; echo "exit=$?"
src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt:16:        fun now(): ActivityTimestamp = ActivityTimestamp(LocalDateTime.now())
src/main/kotlin/io/reflectoring/buckpal/account/domain/BaselineDate.kt:11:        fun now(): BaselineDate = BaselineDate(LocalDateTime.now())
exit=0
```

Literal expectation in the contract was "no matches (exit 1)" — observed exit
is 0 with two matches. **Ruled PASS by the Evaluator** — see the next section
for the explicit reasoning.

### Check 6 — `ActivityJpaEntity.timestamp: LocalDateTime?` retained

```
$ grep -Eq 'var[[:space:]]+timestamp[[:space:]]*:[[:space:]]*LocalDateTime\?' \
    src/main/kotlin/io/reflectoring/buckpal/account/adapter/out/persistence/ActivityJpaEntity.kt; echo "exit=$?"
exit=0
```
PASS. JPA column type and nullability preserved — `git diff --quiet` on the
file also confirms zero changes (see check 7).

### Check 7 — external-contract files untouched in working tree

```
$ git diff --quiet -- .../SendMoneyController.kt;  echo "exit=$?" → 0
$ git diff --quiet -- .../ActivityJpaEntity.kt;    echo "exit=$?" → 0
$ git diff --quiet -- .../ActivityRepository.kt;   echo "exit=$?" → 0
$ git diff --quiet -- src/test/resources/io/reflectoring/buckpal/SendMoneySystemTest.sql; echo "exit=$?" → 0
```
PASS, all four. External HTTP, JPA column, HQL params, and DB fixture intact.

### Check 8 — domain test suite passes

```
$ ./gradlew test --tests "io.reflectoring.buckpal.account.domain.*"
> Task :compileKotlin UP-TO-DATE
> Task :test
> Task :jacocoTestReport
BUILD SUCCESSFUL in 8s
6 actionable tasks: 2 executed, 4 up-to-date
exit=0
```
PASS.

### Check 9 — `./gradlew clean build check`

```
$ ./gradlew clean build check
> Task :clean
> Task :compileKotlin
> Task :compileTestKotlin
> Task :test
> Task :jacocoTestReport
> Task :check
> Task :build
BUILD SUCCESSFUL in 27s
10 actionable tasks: 10 executed
exit=0
```
PASS. ArchUnit `DependencyRuleTests` is part of `:test` and was green
(no failed test reports under `build/test-results/`). Full build green.

### Check 10 — `SendMoneySystemTest` round-trip

```
$ ./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"
> Task :test
> Task :jacocoTestReport
BUILD SUCCESSFUL in 15s
exit=0
```
PASS. Spring context boots, HTTP `POST /accounts/send/{Long}/{Long}/{Long}`
round-trip still works end-to-end.

### Check 11 — no Lombok regressions

```
$ grep -R "import lombok" src/; echo "exit=$?"
exit=1
```
PASS. No `lombok` imports anywhere under `src/`.

## Check 5 — literal vs intent ruling

**Verdict: PASS (literal expectation overridden as a contract-internal
inconsistency; intent fully met).**

Reasoning:

1. The contract's check 5 *expected output* is "no matches (exit 1)", but the
   contract's own Deliverable §1 *mandates* the shape
   `companion object { fun now(): ActivityTimestamp = ActivityTimestamp(LocalDateTime.now()) }`
   in `ActivityTimestamp.kt`. The grep `LocalDateTime\.now\s*\(\s*\)` matches
   that exact body. There is no way to simultaneously satisfy both clauses;
   the contract is self-contradictory.
2. Spec sprint-02 ("Files in scope" + Hard exit criteria) is even more
   explicit: it lists `ActivityTimestamp.now()` as the required factory and
   `Activity(... ActivityTimestamp.now() ...)` as the required Account call
   site. Removing `LocalDateTime.now()` from `ActivityTimestamp.now()` would
   require either (a) routing through `Clock.systemDefaultZone().instant()`
   plumbing the spec does not authorise, or (b) breaking the factory entirely
   — neither is acceptable.
3. The second hit, `BaselineDate.kt:11`, is sprint-01 territory and is
   explicitly listed in the contract's "Unchanged" file list (and again under
   spec "Out of scope"). The Generator could not have removed it without
   reopening sprint-01.
4. Substantively, the check's *intent* is met: every **caller** of raw
   `LocalDateTime.now()` inside `src/main/kotlin/.../account/domain/` is gone.
   `Account.kt` (the only sprint-02 caller candidate) now uses
   `ActivityTimestamp.now()` — confirmed by `git diff -- Account.kt` showing
   `-LocalDateTime.now()` / `+ActivityTimestamp.now()` in both `withdraw` and
   `deposit`. Both factory `now()` bodies are the *boundary* between the
   wall-clock primitive and the VO — exactly where the spec wants the
   `LocalDateTime.now()` to live.
5. Generator was transparent about this in the handoff (Notes §1) rather than
   silently mutating contract or code. That is the correct behaviour.

Ruling: this is recorded under **Bugs found** as a contract-wording overreach
(check 5 should have excluded `ActivityTimestamp.kt` and `BaselineDate.kt`,
or scoped the grep to `Account.kt` only); the sprint passes.

## File scope audit

```
$ git diff --stat HEAD
 .claude/harness/workspace/logs/run-log.md                        | 4 ++++
 .../buckpal/account/adapter/out/persistence/AccountMapper.kt     | 5 +++--
 .../kotlin/io/reflectoring/buckpal/account/domain/Account.kt     | 6 ++----
 .../kotlin/io/reflectoring/buckpal/account/domain/Activity.kt    | 6 ++----
 .../io/reflectoring/buckpal/account/domain/ActivityWindow.kt     | 9 ++++-----
 .../buckpal/account/adapter/out/persistence/AccountMapperTest.kt | 9 +++++----
 .../io/reflectoring/buckpal/account/domain/ActivityTest.kt       | 2 +-
 .../io/reflectoring/buckpal/account/domain/ActivityWindowTest.kt | 6 +++---
 .../kotlin/io/reflectoring/buckpal/common/ActivityTestData.kt    | 9 ++++++---
 9 files changed, 30 insertions(+), 26 deletions(-)
```
Plus one new untracked file:
- `src/main/kotlin/io/reflectoring/buckpal/account/domain/ActivityTimestamp.kt`

Cross-check vs spec sprint-02 "Files in scope":

| Spec entry | Status |
|------------|--------|
| Create `ActivityTimestamp.kt` | NEW (untracked) — present and correct |
| Edit `Activity.kt` | Edited |
| Edit `ActivityWindow.kt` | Edited |
| Edit `Account.kt` | Edited |
| Edit `AccountMapper.kt` | Edited |
| Edit `ActivityWindowTest.kt` | Edited |
| Edit `ActivityTest.kt` | Edited |
| Edit `ActivityTestData.kt` | Edited |
| Edit `AccountMapperTest.kt` (if it constructs/asserts timestamps) | Edited |

Sprint-01 territory not touched (verified by `git diff --quiet`):
- `BaselineDate.kt`, `LoadAccountPort.kt`, `AccountPersistenceAdapter.kt` —
  all exit 0 (no changes).

Sprint-03 territory not touched:
- `AccountMapper.mapToDomainEntity` still has the two positional `Long`s
  (`withdrawalBalance: Long, depositBalance: Long`). No
  `BaselineBalanceFigures` introduced. `AccountMapperTest` still calls with
  `withdrawalBalance = ..., depositBalance = ...` named-args. Correct
  boundary preservation.

No new VO; no rename; no HQL change; no JPA column change. Scope is clean.

`run-log.md` is harness bookkeeping and not in production-source scope.

## ActivityWindow selector inspection

Diff:

```
-    fun getStartTimestamp(): LocalDateTime =
-        activities.minByOrNull(Activity::timestamp)?.timestamp
+    fun getStartTimestamp(): ActivityTimestamp =
+        activities.minByOrNull { it.timestamp.value }?.timestamp
             ?: throw IllegalStateException()
```

Semantic-preservation analysis:
- **Before**: `Activity::timestamp` returned a `LocalDateTime`, sorted by its
  natural `Comparable<LocalDateTime>` order.
- **After**: `{ it.timestamp.value }` extracts the same underlying
  `LocalDateTime` from the `@JvmInline value class` wrapper. Natural order is
  unchanged.
- The returned object is `?.timestamp`, which is now `ActivityTimestamp` — the
  function's declared return type. Type-safe.
- Both `min`/`max` selectors fall back to `throw IllegalStateException()` on
  empty list, same as before.

`ActivityTimestamp` deliberately does **not** implement `Comparable` (the spec
explicitly advises against operator overloads / orderings unless a sprint
needs them — and this one doesn't). Selecting on `.value` is the right idiom;
it costs nothing on the JVM because `value class` erases to `LocalDateTime`.

`ActivityWindowTest` verifies the orderings: `startDate()` is the minimum,
`endDate()` is the maximum, all three are `ActivityTimestamp` on both sides
of `shouldBe`. Passes under `./gradlew clean build check`.

## AccountMapper null handling

```
val timestamp = requireNotNull(activity.timestamp) {
    "ActivityJpaEntity loaded without timestamp"
}
...
Activity(
    ...,
    ActivityTimestamp(timestamp),
    Money.of(amount),
)
```

The pre-sprint-02 `requireNotNull(activity.timestamp)` pattern (matching the
existing pattern for `id`, `ownerAccountId`, `sourceAccountId`,
`targetAccountId`, `amount`) is preserved. Wrapping happens **after**
`requireNotNull`, so `ActivityTimestamp(timestamp)` is never called with null.
On the write path, `activity.timestamp.value` unwraps a non-nullable
`ActivityTimestamp` to its non-nullable `LocalDateTime`. Behaviour matches the
pre-existing AccountMapperTest "throws IllegalArgumentException when
ActivityJpaEntity.timestamp is null" test, which still passes.

## ActivityTestData convenience overload

```
fun withTimestamp(timestamp: ActivityTimestamp): ActivityBuilder = apply { ... }
fun withTimestamp(timestamp: LocalDateTime): ActivityBuilder = apply {
    this.timestamp = ActivityTimestamp(timestamp)
}
```

The canonical overload takes `ActivityTimestamp`; the `LocalDateTime` overload
wraps. This is explicitly allowed by spec sprint-02 ("convenience overload
accepting `LocalDateTime` is *optional* for test readability") and by the
contract's Risk note on `ActivityTestData.withTimestamp(...)`. It does **not**
weaken the type discipline: production code can no longer call
`withTimestamp(LocalDateTime)` because `ActivityTestData` is in
`src/test/kotlin/`. Acceptable.

## ArchUnit / hexagonal layering

ArchUnit `DependencyRuleTests` runs as part of `./gradlew check` (Check 9
above). Build green. No new dependency from `domain` outward — the only
imports added to `domain/Activity.kt`, `domain/ActivityWindow.kt`,
`domain/Account.kt` are intra-package references to `ActivityTimestamp` (which
lives in the same `domain` package), so no layer boundary was crossed.
`AccountMapper.kt` (in `adapter.out.persistence`) imports
`io.reflectoring.buckpal.account.domain.ActivityTimestamp` — adapter → domain
is the *allowed* direction in hexagonal architecture.

## SOLID Analysis

SOLID_VIOLATIONS: NO

### S — Single Responsibility

`ActivityTimestamp` does one thing: wrap a `LocalDateTime` to give it the
domain meaning "when an activity occurred". `Activity`, `ActivityWindow`,
`Account` each retain their pre-existing responsibilities (no concerns were
added or merged). No violation.

### O — Open/Closed

No new `when (x) { is Foo -> ... }` switching introduced. Min/Max selectors
use a lambda over a value class accessor — closed for modification, open for
extension by composition. No violation.

### L — Liskov Substitution

`ActivityTimestamp` is `final` (value class). `Activity`'s contract is wider
in type-safety, not narrower (the parameter became *more* specific, which is
the correct direction). `ActivityWindow`'s `getStartTimestamp` /
`getEndTimestamp` still throw `IllegalStateException` on empty windows —
same exception, same semantics. No violation.

### I — Interface Segregation

No port interface changed in sprint-02. `LoadAccountPort` is the only port
that mentions a timestamp-shaped parameter and it is `BaselineDate`,
untouched this sprint. No empty implementations or
`UnsupportedOperationException` introduced. No violation.

### D — Dependency Inversion

`AccountMapper` (adapter) depends on `ActivityTimestamp` (domain) — adapter →
domain is the inversion-friendly direction. Domain code (`Activity`,
`ActivityWindow`, `Account`, `ActivityTimestamp`) does not import from
adapter / application / persistence packages. No high-level → low-level
concrete-instantiation introduced. No violation.

## Criteria

### Behavioral Correctness — 9.5/10 [threshold 9]

All three gradle test invocations exited 0:
- `./gradlew test --tests "io.reflectoring.buckpal.account.domain.*"` → BUILD SUCCESSFUL.
- `./gradlew clean build check` → BUILD SUCCESSFUL (full suite + ArchUnit).
- `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"` → BUILD SUCCESSFUL.

No tests were modified to make them pass — `AccountMapperTest`'s "throws when
timestamp is null" case still uses `timestamp = null` and still asserts
`shouldThrow<IllegalArgumentException>`. `ActivityTest` and
`ActivityWindowTest` construct `ActivityTimestamp` on both sides of
`shouldBe`, which is the intended Kotest equality path (handoff-spec risk
register §4).

Half-point deduction: nothing material; just the absence of an explicit unit
test for `ActivityTimestamp.now()` itself (we rely on it transitively via the
`Account.deposit`/`Account.withdraw` paths).

### Idiomatic Kotlin — 9/10 [threshold 7]

- `@JvmInline value class ActivityTimestamp(val value: LocalDateTime)` —
  exact textbook idiom.
- `companion object { fun now(): ActivityTimestamp = ... }` — `fun` factory,
  no needless `@JvmStatic`, no top-level pollution.
- `activities.minByOrNull { it.timestamp.value }` — trailing-lambda selector,
  idiomatic, no `Comparator`.
- `val ownerId = id!!` in `Account.deposit` / `withdraw` is pre-existing
  sprint-01 code; the comment justifies it.
- `ActivityTimestamp(timestamp)` constructor call inside `mapToActivityWindow`
  reads cleanly post-`requireNotNull`.
- `withTimestamp(LocalDateTime)` overload uses `apply { ... }` consistently
  with the existing builder style.
- No `var` where `val` would do; no unjustified `!!`; no `lateinit var`;
  no `Optional<T>` mis-mapping; no `@Autowired` field injection introduced.

Minor: the redundant qualifier in `ActivityTimestamp.kt`'s KDoc
(`io.reflectoring.buckpal.account.adapter.out.persistence.ActivityJpaEntity`
fully qualified) makes the comment a little wordy, but accurate and
unambiguous.

### Architectural Integrity — 10/10 [threshold 9]

- Package boundaries respected: VO lives in `account.domain`, adapter imports
  it (allowed direction).
- ArchUnit `DependencyRuleTests` green in `:check`.
- HTTP boundary unchanged: `SendMoneyController.kt` diff empty.
- JPA boundary unchanged: `ActivityJpaEntity.kt`, `ActivityRepository.kt`
  diff empty. SQL fixture diff empty.
- Sprint-01 files untouched: `BaselineDate.kt`, `LoadAccountPort.kt`,
  `AccountPersistenceAdapter.kt` all diff-empty.
- Sprint-03 territory untouched: `AccountMapper.mapToDomainEntity` keeps its
  two-positional-`Long` signature; no `BaselineBalanceFigures` introduced.

### Code Quality — 8.5/10 [threshold 7]

- Concise diffs — only 30 insertions / 26 deletions across 9 files; nothing
  gratuitously rewritten.
- Two `withTimestamp` overloads keep call sites readable without weakening
  the canonical signature.
- `Activity.kt` and `Account.kt` shed their now-unused `LocalDateTime`
  imports; no dead imports introduced.
- KDoc on `ActivityTimestamp` is informative and points readers at the JPA
  boundary, which is exactly where the conversion lives.

Half-point quibbles (not bugs):
- `ActivityWindow.getStartTimestamp` / `getEndTimestamp` still throw
  `IllegalStateException()` with no message — pre-existing, not in this
  sprint's scope to fix.
- KDoc fully-qualified type links could use Kotlin's `@see ActivityJpaEntity`
  shorthand inside the same module, but the explicit form is unambiguous.

## Bugs found

| # | File:Line | Severity | Defect | Suggested fix |
|---|-----------|----------|--------|---------------|
| 1 | `contracts/sprint-02-contract.md:88-93` | Low (contract bug, not code bug) | check 5 (`LocalDateTime.now()` grep over `account/domain/`) demands "no matches" but Deliverable §1 of the same contract mandates the body `fun now(): ActivityTimestamp = ActivityTimestamp(LocalDateTime.now())`. The check also unavoidably scans sprint-01's `BaselineDate.kt`, which is on this sprint's "Unchanged" list. | In a retrospective revision, tighten check 5 to either (a) exclude both `ActivityTimestamp.kt` and `BaselineDate.kt`, or (b) restrict the grep to `account/domain/Account.kt` (the only sprint-02 *caller* that could leak the primitive). |

No production-code defects found.

## Contract checklist

| # | Check | Verdict | Evidence |
|---|-------|---------|----------|
| 1 | `ActivityTimestamp.kt` exists | PASS | `test -f` → exit 0 |
| 2 | `@JvmInline value class ActivityTimestamp(val value: LocalDateTime)` | PASS | three-step grep → exit 0 |
| 2b | `companion object` + `fun now(): ActivityTimestamp` | PASS | two-step grep → exit 0 |
| 3 | `Activity.timestamp: ActivityTimestamp` | PASS | grep → exit 0; file read confirms both ctors |
| 4 | `ActivityWindow.getStartTimestamp` / `getEndTimestamp` return `ActivityTimestamp` | PASS | two matching lines |
| 5 | no raw `LocalDateTime.now()` in `account/domain/` | PASS (intent met; literal expectation ruled a contract-internal inconsistency — see "Check 5 ruling") | two matches, both inside VO `companion object { fun now() }` factories required by the contract itself |
| 6 | `ActivityJpaEntity.timestamp: LocalDateTime?` | PASS | grep → exit 0; `git diff --quiet` → exit 0 |
| 7 | external-contract files unchanged | PASS | all four `git diff --quiet` → exit 0 |
| 8 | `./gradlew test --tests "io.reflectoring.buckpal.account.domain.*"` | PASS | BUILD SUCCESSFUL, exit 0 |
| 9 | `./gradlew clean build check` | PASS | BUILD SUCCESSFUL, 27s, ArchUnit green |
| 10 | `./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"` | PASS | BUILD SUCCESSFUL, exit 0 |
| 11 | no `import lombok` | PASS | exit 1 (no matches) |

## Notes

- `gitleaks` was unavailable on this Linux host; SKIPPED per the evaluator
  playbook (does not constitute an automatic FAIL when tool is absent).
- The Generator's self-check output matched my re-runs exactly across all 11
  checks. The handoff was honest about check 5, including the
  literal-vs-intent caveat. That kind of transparency is what the harness
  rewards.
- Test data builder retains a `withTimestamp(LocalDateTime)` convenience
  overload that allows existing sprint-01 callers
  (e.g. `AccountPersistenceAdapterTest`'s
  `defaultActivity().withTimestamp(LocalDateTime.of(...))`) to keep compiling
  without a second touch. This is exactly the optional affordance the spec
  sprint-02 paragraph on `ActivityTestData` allows.
- `Account.deposit` / `withdraw` continue to instantiate `Activity` with
  positional arguments; with `ActivityTimestamp` as a distinct type, the JVM
  compiler now catches arg-order swaps that would previously have
  type-checked as `LocalDateTime`. Type-safety upgrade delivered.

## Verdict

Sprint-02 PASSES. The `ActivityTimestamp` value class is in place with the
exact shape required (`@JvmInline value class ActivityTimestamp(val value:
LocalDateTime)` + `companion object { fun now() }`), all callers in
`Account.kt` were retyped, `ActivityWindow`'s public API now returns the VO,
the `AccountMapper` boundary correctly converts in both directions, and every
out-of-scope file (HTTP controller, JPA entity, repository, SQL fixture,
sprint-01 territory, sprint-03 territory) is bit-identical to its
pre-sprint-02 form. ArchUnit green, full build green, system test green, no
Lombok creep. The only blemish is a self-inconsistent acceptance check 5 in
the contract itself, which is a wording bug not a code bug — recorded in the
bugs table for retrospective tightening, not held against the Generator.
