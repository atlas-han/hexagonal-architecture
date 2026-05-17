# Evaluation Criteria — Kotlin Migration

This document is read by **both** the Generator (so they know the bar) and the
Evaluator (so they grade consistently). Treat the wording as load-bearing —
the source article noted that even small phrasing tweaks measurably steer the
Generator.

Four criteria. Each is scored 0–10. Weights and hard thresholds below.

| Criterion                 | Weight | Hard floor |
|---------------------------|--------|------------|
| Behavioral Correctness    | 35 %   | 9 / 10     |
| Idiomatic Kotlin          | 30 %   | 7 / 10     |
| Architectural Integrity   | 20 %   | 9 / 10     |
| Code Quality              | 15 %   | 7 / 10     |

A sprint **FAILS** if *any* criterion lands below its hard floor — even if
the weighted total is high. This mirrors the article's design: a single
broken core feature should not be papered over by good scores elsewhere.

---

## 1. Behavioral Correctness (35 %, floor 9)

> Does the Kotlin code preserve every observable behavior of the original
> Java code?

**10** — All existing JUnit tests pass. All ArchUnit rules pass.
`./gradlew check` is green. The Spring Boot app boots and the
`POST /accounts/send/...` endpoint responds identically (same status, same
side effects on the H2 DB) for the SQL fixture under
`src/test/resources/.../SendMoneySystemTest.sql`.

**7–8** — Tests pass but a non-test artifact regressed (e.g., a Jackson
serialization shape changed; HTTP status remained but body field order or
casing shifted in a way callers could see).

**4–6** — A test was disabled, weakened, or rewritten to make it pass; OR a
silent behavior change was introduced (e.g., `BigDecimal` rounding mode flips
because of an unboxing).

**0–3** — A test is broken, or the app no longer boots.

**Hard floor: any failing test or ArchUnit rule = score 8 max = FAIL.**

### Verification commands

```
./gradlew clean
./gradlew compileKotlin compileTestKotlin
./gradlew test
./gradlew check
./gradlew bootRun & sleep 12; curl -i -X POST \
  'http://localhost:8080/accounts/send/1/2/500'; kill %1
```

---

## 2. Idiomatic Kotlin (30 %, floor 7)

> Is this *Kotlin*, or is it Java with a `.kt` extension?

A human Kotlin developer should recognize deliberate use of the language.
Default-translated, Java-shaped Kotlin fails here.

**10** — Strong, deliberate use of Kotlin features where they fit:
- `data class` for value-bearing types (`Money`, `Activity`,
  `SendMoneyCommand`, `AccountId`, `ActivityId`).
- `val` over `var` by default; immutability preserved.
- Null safety leveraged: `T?` at boundaries that can genuinely be null;
  non-null elsewhere. No `Optional<T>` left in newly-converted code.
- Lombok 100% removed: no `@Slf4j`, `@Value`, `@Builder`,
  `@RequiredArgsConstructor`, `@Getter` / `@Setter`.
- Primary-constructor injection in Spring components (no `@Autowired field`).
- Extension functions or scope functions (`apply`, `let`, `also`, `run`)
  used where they materially improve clarity — *not* sprinkled for style.
- `companion object` only when needed (static-like factories, JVM constants).
- Operator overloads on `Money` (`plus`, `minus`, `compareTo`) used in
  domain logic.

**7–8** — Solid translation, but with 1–2 missed idioms: e.g., classes that
should be `data class` are plain `class`; one or two `Optional<T>` survived
at internal call sites; `!!` used where a smart-cast would have worked.

**4–6** — Reads like Java: `var` everywhere, `lateinit var` to dodge
constructors, fields injected via `@Autowired`, Lombok-style getter funcs
written by hand.

**0–3** — Still imports Lombok, or uses `Object` / `Boolean.TRUE` style
patterns. This is failure.

**Hard floor: any `import lombok.*` in converted Kotlin = FAIL.**

### Anti-patterns the Evaluator should grep for

```
grep -R "import lombok"           src/main/kotlin src/test/kotlin
grep -R "Optional<"               src/main/kotlin
grep -Rn "!!\b"                   src/main/kotlin     # each occurrence
                                                       # must have a one-line
                                                       # explanatory comment
grep -Rn "lateinit var"           src/main/kotlin
grep -Rn "@Autowired"             src/main/kotlin
```

---

## 3. Architectural Integrity (20 %, floor 9)

> Does the conversion preserve the hexagonal architecture?

The repo's whole point is to demonstrate hexagonal architecture. Breaking
package boundaries during a "format change" migration would defeat the
exercise.

**10** — Every original package path under `io.reflectoring.buckpal.**`
still exists. ArchUnit `HexagonalArchitecture` rules still pass.
`@WebAdapter` / `@PersistenceAdapter` / `@UseCase` annotations still mark
the right layers. No new cross-layer imports (e.g., `domain` importing
`adapter`).

**7–9** — Packages preserved, but a class was widened (e.g., a
package-private adapter became `public`/`open`) or annotation usage drifted.

**0–6** — ArchUnit fails, or a domain class now depends on a Spring /
adapter symbol.

**Hard floor: ArchUnit `DependencyRuleTests` red = FAIL.**

### Verification

```
./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"
./gradlew test --tests "io.reflectoring.buckpal.archunit.*"
find src/main/kotlin -type d | sort
diff <(find src/main/java -type d | sed 's|src/main/java/||' | sort) \
     <(find src/main/kotlin -type d | sed 's|src/main/kotlin/||' | sort)
```

---

## 4. Code Quality (15 %, floor 7)

> Is the converted code clean, consistent, and free of red flags?

**10** — Kotlin compiler emits zero warnings. Consistent naming
(`UpperCamelCase` types, `lowerCamelCase` props/funs). No commented-out
code. No `TODO` left without an attached follow-up sprint number. Each
file's content matches its filename. Imports are sorted, no `import *` for
domain packages.

**7–8** — Compiler is clean but there are isolated nits: a leftover
`// TODO`, two slightly awkward function names, one file with two unrelated
classes.

**4–6** — Multiple compiler warnings; inconsistent spacing/indent;
copy-pasted comments referring to Java that no longer apply.

**0–3** — Won't compile cleanly without warnings; structural mess.

**Hard floor: kotlinc warnings count > 0 across the converted sprint
scope = score 6 max = FAIL.**

---

## Few-shot scoring examples

The article emphasized few-shot calibration of the Evaluator. Below are
three reference outcomes to anchor scoring.

### Example A — Solid sprint

```
Sprint 2 — account/domain converted.
- Money: data class wrapping BigDecimal. operator funs for +, -, compareTo.
- Activity, ActivityWindow: data classes; val fields.
- Account: class with primary constructor, methods preserved 1:1.
- All 12 tests under account.domain still pass.
- ArchUnit green.
- 0 kotlinc warnings.
- 0 `!!`, 0 `lateinit`, 0 `Optional<>` in src/main/kotlin/.../domain.
```

Scores: BC 10 · IK 9 · AI 10 · CQ 9 · **Weighted 9.5 — PASS.**

### Example B — Functional but lazy

```
Sprint 2 — account/domain converted.
- All classes converted to `class` (not `data class`); manual equals/hashCode.
- `var` used for most fields where `val` would have worked.
- 1 use of `!!` in Account.calculateBalance with no comment.
- Tests pass; ArchUnit green; 2 kotlinc warnings about unused parameters.
```

Scores: BC 9 · IK 6 · AI 10 · CQ 7 ·
**Hard floor breach on Idiomatic Kotlin (6 < 7) — FAIL.**
Evaluator action: ask Generator to apply `data class`, drop `!!`, remove
unused params.

### Example C — Subtle correctness regression

```
Sprint 2 — account/domain converted.
- Idiomatic Kotlin score would be 9 in isolation.
- BUT Money(BigDecimal("10.0")) and Money(BigDecimal("10.00")) used to be
  equal under the old @Value implementation (compareTo); the new data class
  uses BigDecimal.equals which is scale-sensitive — one existing test
  catches this and fails.
```

Scores: BC 4 · IK 9 · AI 10 · CQ 8 ·
**Behavioral Correctness < 9 — FAIL.**
Evaluator action: require Generator to either override `equals` to use
`compareTo`, or normalize scale in the constructor.
