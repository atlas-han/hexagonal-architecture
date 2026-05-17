# Sprint 9 Review

STATUS: PASS
WEIGHTED SCORE: 9.55

## Criteria

### Behavioral Correctness — 10/10 [threshold 9]

Re-ran every mandated command with `JAVA_HOME=corretto-17.0.13`:

- `./gradlew clean` → BUILD SUCCESSFUL (exit 0)
- `./gradlew compileKotlin compileTestKotlin` → BUILD SUCCESSFUL (exit 0)
- `./gradlew test` → BUILD SUCCESSFUL (exit 0)
- `./gradlew check` → BUILD SUCCESSFUL (exit 0)
- `./gradlew clean build` → BUILD SUCCESSFUL (exit 0)
- Targeted suites all green:
  - `SendMoneyServiceTest` — 2/2 (exit 0)
  - `SendMoneySystemTest` — 1/1 (exit 0; exercises HTTP→Spring→JPA→H2, proves `application.yml` → `BuckPalConfigurationProperties` → `MoneyTransferProperties` wiring)
  - `BuckPalApplicationTests` — 1/1 (exit 0)
  - `DependencyRuleTests` — 2/2 (exit 0)

`build/test-results/test/TEST-*.xml` per-suite parse:
- `BuckPalApplicationTests`: 1/0/0
- `SendMoneySystemTest`: 1/0/0
- `DependencyRuleTests`: 2/0/0
- `AccountTest`: 4/0/0
- `ActivityWindowTest`: 3/0/0
- `SendMoneyServiceTest`: 2/0/0
- `SendMoneyControllerTest`: 1/0/0
- `AccountPersistenceAdapterTest`: 2/0/0
- **Totals: 16 tests / 0 failures / 0 errors / 0 skipped.**

`SendMoneyServiceTest.kt` assertions are byte-identical to the Sprint-8
committed version (verified via `git diff HEAD`). The only changes are
`account.getId().get()` → `account.id!!`, `given(account.getId()).willReturn(Optional.of(id))`
→ `given(account.id).willReturn(id)`, and the two-stage Stream `.map(Account::getId).map(Optional<AccountId>::get)`
collapses to `.map { it.id!! }`. The `import java.util.Optional` line is
removed. **No assertion was weakened, no `then(...)` chain altered, no
`times(...)` argument changed.**

### Idiomatic Kotlin — 9/10 [threshold 7]

- `Account.id` now exposed directly as a primary-constructor `val id: AccountId?`
  (Account.kt:14). No `_id` shim, no `Optional<AccountId>`, no
  `@get:JvmName("getIdOrNull")`. `import java.util.Optional` is gone.
- `open val id` (Account.kt:14): non-obvious but justified. Kotlin compiles
  property accessors as `final` by default; `SendMoneyServiceTest` uses
  Mockito subclass-mocking which requires overridable methods. Without
  `open`, `given(account.id).willReturn(id)` fails with
  `MissingMethodInvocationException`. The Generator added an inline
  one-line comment at Account.kt:12-13 explaining this. The alternative
  (`mockito-inline` / `mockito-kotlin`) was out of sprint scope. **This is
  the minimal-blast-radius choice and I accept it.** A future refactor
  could collapse it back to `val` once `mockito-inline` is on the test
  classpath, but that is out of scope for the migration.
- `.map { it.id!! }` lambda (SendMoneyServiceTest.kt:110) is idiomatic
  replacement for the two-stage Java Stream pipeline.
- `!!` audit: `grep -Rn '!!' src/main/kotlin` → 2 hits, both in
  `Account.kt` (lines 37, 58), both inside `withdraw`/`deposit`. The
  comment at Account.kt:35-36 explains the invariant for `withdraw`;
  `deposit` reuses the same invariant but does not repeat the comment.
  Minor nit (see Code Quality).
- `lateinit var`/`@Autowired` in `src/main/kotlin` → 0 hits. The hits in
  `src/test/kotlin` (SendMoneySystemTest, AccountPersistenceAdapterTest,
  SendMoneyControllerTest) are standard Spring `@SpringBootTest` /
  `@WebMvcTest` idioms committed in Sprint 8 — not in this sprint's
  scope, and the spec's anti-pattern check explicitly targets
  `src/main/kotlin`.
- `Optional<...>` grep: 0 hits anywhere (`src/main/kotlin`, `src/test/kotlin`).
- `import lombok` grep: 0 hits.
- README and build.gradle now identify the project as Kotlin-first.

The deduction is for the `open` keyword needed to work around Mockito —
not a defect in this sprint, but it is the one idiom-tax point that
keeps the score from a 10.

### Architectural Integrity — 10/10 [threshold 9]

- `DependencyRuleTests` 2/2 PASS (verified targeted run, exit 0).
- `./gradlew check` BUILD SUCCESSFUL — picks up ArchUnit rules baked into
  the test suite.
- Package tree under `src/main/kotlin/io/reflectoring/buckpal/` unchanged:
  `account/adapter/{in,out}/{web,persistence}`, `account/application/{port,service}/{in,out}`,
  `account/domain`, `common`, root. All original Java packages still
  exist as Kotlin packages.
- `Account.kt` keeps `private constructor`, factories `withId`/`withoutId`
  on the companion object — JVM ABI for persistence-adapter call sites
  preserved.
- No new cross-layer imports introduced.
- `SendMoneySystemTest` proves the bean topology and
  `@ConfigurationProperties` binding still work end-to-end.

### Code Quality — 9/10 [threshold 7]

- `./gradlew clean compileKotlin compileTestKotlin` emits **zero kotlinc
  warnings** (only a Gradle internal API deprecation warning unrelated
  to source, present since Sprint 0).
- `git status` shows exactly the 4 files declared in the handoff
  (`Account.kt`, `SendMoneyServiceTest.kt`, `build.gradle`, `README.md`).
  No surprise file edits.
- `build.gradle` minimal-edit: only removals listed in the contract.
  Kotlin plugins, all `compileKotlin` / `compileTestKotlin` blocks, and
  all other deps untouched (verified via `git diff`).
- README minimal-edit: 3 lines changed (`Java`→`Kotlin`, Lombok line
  dropped, JDK 11 line reworded). The JDK 11 rewording ("or newer; the
  project targets JVM 11 bytecode") is technically accurate and not
  flagged.
- Minor nit: `Account.kt:58` (`val ownerId = id!!` in `deposit`) reuses
  the invariant explained at lines 35-36 (over `withdraw`) but has no
  inline comment of its own. The rubric prefers each `!!` to have an
  explanatory comment. Not a hard fail — the invariant is documented
  once, immediately above the first occurrence, and both occurrences are
  in the same file under symmetric methods. Suggested fix: duplicate the
  2-line comment above line 58, or move it to a KDoc on the class /
  companion.

## Bugs found

| File:Line | Defect | Suggested fix |
|-----------|--------|---------------|
| (none — no functional defects) | — | — |

## Contract checklist

All 20 contract acceptance checks:

- [x] `find src -name '*.java'` → 0 hits. (Verified empty; only empty Java dirs remain.)
- [x] `grep -R "lombok" build.gradle` → 0 hits.
- [x] `grep -R "import lombok" src` → 0 hits.
- [x] `grep -R "Optional<" src/main/kotlin` → 0 hits.
- [x] `grep -R "Optional<" src/test/kotlin` → 0 hits.
- [x] `grep -R "import java.util.Optional" src` → 0 hits.
- [x] `grep -n "getId" src/main/kotlin/.../Account.kt` → 0 hits. (Kotlin synthesises `getId()` on the JVM but the source uses property syntax.)
- [x] `grep -n "apply plugin: 'java'" build.gradle` → 0 hits.
- [x] `grep -n "apply plugin: 'java-library'" build.gradle` → 0 hits.
- [x] `grep -n "compileJava {" build.gradle` → 0 hits.
- [x] `./gradlew clean build` → BUILD SUCCESSFUL (exit 0).
- [x] `./gradlew test` → BUILD SUCCESSFUL, 16/16 (parsed XMLs: 1+2+2+4+3+1+2+1=16, 0 fail, 0 err).
- [x] `./gradlew check` → BUILD SUCCESSFUL; `DependencyRuleTests` 2/2 green.
- [x] `./gradlew test --tests "...SendMoneyServiceTest"` → 2/2 PASS.
- [x] `./gradlew test --tests "...SendMoneySystemTest"` → 1/1 PASS.
- [x] `./gradlew test --tests "...BuckPalApplicationTests"` → 1/1 PASS.
- [x] 0 kotlinc warnings repo-wide. Verified via `--warning-mode all` filter.
- [x] `grep -i "lombok" README.md` → 0 hits.
- [x] `grep -c "Kotlin" README.md` → 1 (≥ 1 required).
- [x] (Bonus, from spec section 2 invariants) ArchUnit + Spring boot context + HTTP path all green via SendMoneySystemTest.

## Verdict

Sprint 9 delivers a clean migration finish. All 16 tests green, 0 kotlinc
warnings, 0 Lombok references anywhere, 0 `.java` files under `src/`, 0
`Optional<...>` in source. The `Account.id` Optional shim is collapsed
to an idiomatic `val id: AccountId?` primary-constructor property; the
single non-obvious choice (`open val id`) is necessary to keep Mockito
subclass mocking working in `SendMoneyServiceTest` and is documented
inline. `SendMoneyServiceTest.kt`'s diff against Sprint 8 is purely
Optional-to-property syntax — assertions, BDD verification chains, and
`times(...)` arguments are byte-identical. `build.gradle` and `README.md`
edits are minimal and exactly per spec. `SendMoneySystemTest` exercises
the full HTTP→Spring→JPA→H2 path and the `application.yml` → 
`BuckPalConfigurationProperties` → `MoneyTransferProperties` wiring, so
the spec section-2 invariants hold.

Generator may now commit sprint 9. The Kotlin migration is complete.
