# Sprint 3 Review

STATUS: PASS
WEIGHTED SCORE: 9.45

## Scope verification

`git diff --stat HEAD` shows exactly 6 Java port files deleted:

```
.../port/in/GetAccountBalanceQuery.java            | 10 -------
.../port/in/SendMoneyCommand.java                  | 34 ----------------------
.../port/in/SendMoneyUseCase.java                  | 11 -------
.../port/out/AccountLock.java                      | 11 -------
.../port/out/LoadAccountPort.java                  | 11 -------
.../port/out/UpdateAccountStatePort.java           |  9 ------
6 files changed, 86 deletions(-)
```

Untracked: the 6 `.kt` files under `src/main/kotlin/.../port/{in,out}/`, the
sprint-03 contract, and the sprint-03 handoff. Zero touches to `src/test/`,
zero touches to any other production file. Scope is bounded.

## Criteria

### Behavioral Correctness — 10/10 [threshold 9]

Re-ran every required command from a clean state:

| Command | Result |
|---------|--------|
| `./gradlew clean` | BUILD SUCCESSFUL |
| `./gradlew compileKotlin compileJava compileTestKotlin compileTestJava` | BUILD SUCCESSFUL |
| `./gradlew test` | BUILD SUCCESSFUL |
| `./gradlew check` | BUILD SUCCESSFUL |
| `./gradlew test --tests "...SendMoneyServiceTest"` | tests=2, failures=0, errors=0 |
| `./gradlew test --tests "...SendMoneyControllerTest"` | tests=1, failures=0 |
| `./gradlew test --tests "...DependencyRuleTests"` | tests=2, failures=0 |

Aggregated from `build/test-results/test/TEST-*.xml`: 16/16 green
(BuckPalApplicationTests=1, AccountTest=4, ActivityWindowTest=3,
SendMoneySystemTest=1, SendMoneyServiceTest=2, SendMoneyControllerTest=1,
AccountPersistenceAdapterTest=2, DependencyRuleTests=2). The system test
`SendMoneySystemTest` (HTTP `POST /accounts/send/1/2/500` on H2) passes,
which is the strongest live signal — it actually constructs
`SendMoneyCommand` through the Spring stack and the data-class +
`SelfValidating` interop survives reflection-driven Bean Validation.

**Weakness:** the Bean Validation NULL-rejection path is statically guarded
(`@field:NotNull` lands on the JVM field — verified by `javap -v` below)
but is NOT exercised at runtime by any green test. A future refactor that
accidentally drops the site target would not trip CI. Documented as a
pre-existing test gap by the Generator; remains a real residual risk.

### Idiomatic Kotlin — 10/10 [threshold 7]

Sampled all 6 files (small enough to fully audit, not just sample):

- `SendMoneyCommand.kt:8` — `data class` with three `val` properties, all
  with `@field:NotNull` site target. Trailing comma after `money`. Clean.
- `SendMoneyCommand.kt:14` — `init { validateSelf() }` block, single
  statement, mirrors original Java intent.
- `SendMoneyUseCase.kt:3` / `GetAccountBalanceQuery.kt:6` /
  `LoadAccountPort.kt:6` / `UpdateAccountStatePort.kt:5` /
  `AccountLock.kt:5` — minimal interfaces, no default methods, no
  `@JvmDefault`, no companion objects, no annotations.
- Anti-pattern grep `grep -RE "(!!|lateinit|Optional<|@Autowired)"` against
  `src/main/kotlin/.../port` → 0 hits.
- `grep -R "import lombok" src/main/kotlin src/test/kotlin` → 0 hits.
- `package ... .`in`` backtick escaping present in all 3 in-files; Java
  callers in `SendMoneyController.java:3-4` and `SendMoneyService.java:3-7`
  reference the unescaped FQN `...port.in.*` and still compile (Java
  `in` is not a keyword).
- `data class` extending abstract non-data `SelfValidating<T>()` — Kotlin
  1.6.21 supports this; `javap -p` confirms compiler-synthesized
  `equals(Object)`, `hashCode()`, `toString()`, `copy(...)`, `component1..3`
  on the class. No hand-rolled overrides, matching the contract's amended
  guidance.
- Trailing-comma style is consistent with Sprint 2.

**Weakness:** the trailing comma on line 11 is fine, but the interfaces
have a vestigial blank line between the package and the `interface` keyword
(e.g., `SendMoneyUseCase.kt:2`, `AccountLock.kt:2`). Trivial, not worth a
nit at scale, but it's not what an idiomatic Kotlin formatter would
emit. Score stays at 10 — too small to dock.

### Architectural Integrity — 10/10 [threshold 9]

- `./gradlew check` green → ArchUnit rules pass.
- `DependencyRuleTests` (2 tests) explicitly green.
- All 6 files live at the exact paths the contract specified; no package
  drift.
- Audited imports in all 6 files:
  - `port.in` files import only `account.domain.*`, `common.SelfValidating`,
    and `javax.validation.constraints.NotNull`. None import `port.out.*`.
  - `port.out` files import only `account.domain.*` and `java.time.LocalDateTime`.
    None import `port.in.*`.
- No `port.*` import of `adapter.*` or any Spring symbol.

**Weakness:** the `in` package is now declared in Kotlin source as
``port.`in` `` while ArchUnit string assertions and Java callers reference
it as `port.in`. The JVM doesn't care, but if a future Kotlin-only
refactor uses a `KClass<*>.java.`package`.name` assertion, the escaping
could trip someone up. Currently invisible — no rule depends on it.

### Code Quality — 10/10 [threshold 7]

- 0 kotlinc warnings on `compileKotlin --rerun-tasks --warning-mode=all`
  for the 6 new files (no `w:` lines).
- File-per-class organisation; filename matches the single declared type
  in each file.
- Imports are minimal and ordered; no `import *`.
- No commented-out code, no `TODO` markers, no leftover Java-style doc
  comments.

**Weakness:** `SendMoneyCommand.kt` does not carry a KDoc comment
explaining *why* `@field:` site target is mandatory (load-bearing for
Bean Validation correctness). The contract explains it; the source file
does not. Future readers refactoring without context could remove the
site target and silently break the null guard (which no test exercises).

## Critical independent verification: `javap` on `SendMoneyCommand.class`

`javap -p` confirms class shape (constructor, getters, `equals(Object)`,
`hashCode()`, `toString()`, `copy`, `component1..3`):

```
public final class io.reflectoring.buckpal.account.application.port.in.SendMoneyCommand extends io.reflectoring.buckpal.common.SelfValidating<...> {
  private final ...AccountId sourceAccountId;
  private final ...AccountId targetAccountId;
  private final ...Money money;
  public SendMoneyCommand(...AccountId, ...AccountId, ...Money);
  public final ...AccountId getSourceAccountId();
  public final ...AccountId getTargetAccountId();
  public final ...Money getMoney();
  public final ...AccountId component1();
  public final ...AccountId component2();
  public final ...Money component3();
  public final ...SendMoneyCommand copy(...AccountId, ...AccountId, ...Money);
  public static ...SendMoneyCommand copy$default(..., int, java.lang.Object);
  public java.lang.String toString();
  public int hashCode();
  public boolean equals(java.lang.Object);
}
```

Note: the class is **`public final`**. The contract speculated "data class
can be final, but extending SelfValidating may keep it open through
inheritance — verify what shows up." Kotlin emits `final` here because
`data class` is implicitly final and the abstract parent does not change
that. This is fine: the Java original `public class SendMoneyCommand
extends SelfValidating<SendMoneyCommand>` was non-final, but no caller
extends it. No observable behavior change.

`javap -p -v` confirms `@field:NotNull` lands on the JVM field, not just
on the parameter or the getter. From `SendMoneyCommand.class` line 127–155:

```
private final ...AccountId sourceAccountId;
  flags: (0x0012) ACC_PRIVATE, ACC_FINAL
  RuntimeVisibleAnnotations:
    0: #87()
      javax.validation.constraints.NotNull
  RuntimeInvisibleAnnotations:
    0: #11()
      org.jetbrains.annotations.NotNull
```

Identical block for `targetAccountId` and `money`. **The Bean Validation
guard is not just latent — it is structurally correct at the bytecode
level.** Hibernate Validator's default reflection strategy reads field
annotations; it will see all three `@NotNull`s and reject a null at
`validateSelf()` time.

Constructor bytecode (offsets 22–34) shows fields are assigned BEFORE
`validateSelf()` is invoked (offset 39), so the validation cannot observe
half-initialised state:

```
22-24: putfield sourceAccountId
27-29: putfield targetAccountId
32-34: putfield money
38-39: invokevirtual validateSelf
```

(Caveat at offset 19: `SelfValidating.<init>()` runs *before* the
`putfield`s, but `SelfValidating` only initialises its own validator
property, so it cannot observe the data-class fields prematurely. Safe.)

## Bugs found

None. No defects, no scope violations, no test modifications, no Lombok
imports, no broken acceptance checks.

| File:Line | Defect | Suggested fix |
|-----------|--------|---------------|
| —         | —      | —             |

## Contract checklist

| Check | Result | Evidence |
|-------|--------|----------|
| 0 Java files under `port/` | PASS | `find ... -name '*.java'` → 0 |
| 6 Kotlin files under `port/` | PASS | `find ... -name '*.kt'` → 6 |
| No `import lombok` in port/ | PASS | `grep` → empty (exit 1) |
| 3 × `@field:NotNull` in SendMoneyCommand.kt | PASS | `grep -c` → 3 |
| `SelfValidating<SendMoneyCommand>` × 1 | PASS | `grep -c` → 1 |
| `validateSelf()` × 1 in `init` block | PASS | `grep -c` → 1; verified in source line 14–16 |
| `clean compileKotlin compileJava compileTestKotlin compileTestJava` | PASS | BUILD SUCCESSFUL |
| `./gradlew test` — 16/16 pass | PASS | aggregated from TEST-*.xml |
| `SendMoneyServiceTest` | PASS | 2/2 |
| `SendMoneyControllerTest` | PASS | 1/1 |
| `DependencyRuleTests` | PASS | 2/2 |
| 0 kotlinc warnings on 6 new files | PASS | `compileKotlin --rerun-tasks --warning-mode=all` → no `w:` lines |
| Anti-pattern grep `(!!|lateinit|Optional<|@Autowired)` in port/ | PASS | empty |
| `^data class SendMoneyCommand` × 1 | PASS | enforces evaluator's Phase A `data class` mandate |

14/14 mechanical contract checks PASS.

## Verdict

This is a textbook hexagonal-architecture sprint. Six files, six 1:1
conversions, zero scope creep, zero anti-patterns, zero compiler warnings,
zero test regressions. The two interesting decisions — `data class`
extending non-data `SelfValidating<T>()`, and `@field:NotNull` site
target — were forced by Phase A inline comments and the Generator
implemented both correctly. The `javap` bytecode inspection confirms the
Bean Validation guard is structurally correct (field-level annotation,
fields assigned before `validateSelf()`), even though no green test
exercises the null path — that's a pre-existing test gap the contract
explicitly punted to Sprint 8.

Stance on the `data class` + `SelfValidating` inheritance approach: it
is the **right call**, not a workaround. Kotlin 1.6.21 supports this
combination; the synthesized field-only `equals`/`hashCode`/`toString` are
semantically identical to Lombok's
`@Value @EqualsAndHashCode(callSuper = false)`. Hand-rolling these overrides
(as the contract's pre-amended snippet showed) would invite drift; the
data-class auto-generation eliminates that risk. The bytecode shows the
parent constructor runs before field assignment — harmless because
`SelfValidating` only initialises its own `validator` field. The only
residual concern is the latent (untested) null-rejection path; that is a
test-suite shortcoming, not a code-quality shortcoming.

Weighted score: BC 10×0.35 + IK 10×0.30 + AI 10×0.20 + CQ 10×0.15 = 10.00,
adjusted down to **9.45** because each criterion carries one
forced-skeptical weakness (untested null path, vestigial blank lines,
escaped-package fragility in hypothetical KClass assertions, missing
load-bearing KDoc). None of these is grounds for a deduction below the
hard floor. **PASS.**
