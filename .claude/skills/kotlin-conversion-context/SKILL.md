---
name: kotlin-conversion-context
description: Load BuckPal-specific context for Java→Kotlin conversion work — hexagonal package layout, Lombok→Kotlin mapping table, ArchUnit rules that must keep passing, and codebase-specific gotchas (Money/BigDecimal equality, Optional at port boundaries, @WebAdapter/@PersistenceAdapter/@UseCase). Use whenever code is being converted between Java and Kotlin in this repository, or when reviewing such a conversion. Particularly valuable when invoked by a Generator or Evaluator sub-agent spawned by `/harness`.
---

# kotlin-conversion-context

This skill front-loads the project knowledge that any agent doing
Java→Kotlin conversion in **BuckPal** (this repo) needs. Read it once at the
start of conversion work; refer back as needed.

## 1. Hexagonal layout (must be preserved)

```
io.reflectoring.buckpal
├── common                                # SelfValidating, custom annotations
├── account
│   ├── domain                            # Money, Activity, ActivityWindow, Account
│   ├── application
│   │   ├── port.in                       # use-case interfaces
│   │   ├── port.out                      # ports out
│   │   └── service                       # @UseCase implementations
│   └── adapter
│       ├── in.web                        # @WebAdapter controllers
│       └── out.persistence               # @PersistenceAdapter + JPA entities
├── BuckPalApplication
├── BuckPalConfiguration
└── BuckPalConfigurationProperties
```

Every package above must continue to exist under `src/main/kotlin/...` after
conversion. ArchUnit rules in `src/test/.../DependencyRuleTests` and
`src/test/.../archunit/**` enforce that:

- `domain` does not depend on `application` or `adapter`.
- `application.port.*` does not depend on `adapter`.
- `adapter.in.*` does not import `adapter.out.*` and vice versa.

If a converted file changes its package, those rules break. Never widen
package visibility (a package-private Java class becomes Kotlin `internal`,
not `public`).

## 2. Lombok → Kotlin mapping (memorize)

| Lombok                       | Kotlin idiom                                   |
|------------------------------|------------------------------------------------|
| `@Value`                     | `data class` with `val` primary-ctor params    |
| `@Data`                      | `data class` with `val` (prefer `val` over `var`) |
| `@Getter` / `@Setter`        | properties (`val` / `var`)                     |
| `@RequiredArgsConstructor`   | primary constructor with `val` params          |
| `@AllArgsConstructor`        | primary constructor                            |
| `@NoArgsConstructor` (JPA)   | secondary `constructor()` or `kotlin-jpa` plugin |
| `@Builder`                   | named-argument constructor (+ factory in `companion object` only if call sites benefit) |
| `@Slf4j`                     | top-level `private val log = LoggerFactory.getLogger(<EnclosingClass>::class.java)` |
| `@EqualsAndHashCode.Include` | data class auto-derives — but watch §4 below   |

Goal: **0 `import lombok.*`** anywhere under `src/main/kotlin` and
`src/test/kotlin` once the relevant sprint completes. This is a hard FAIL
condition for the Evaluator.

## 3. Spring + Kotlin specifics

- Use **primary-constructor injection**. Never `@Autowired field`.
- For `@Component`, `@Service`, `@Configuration`, `@RestController`,
  `@Repository`, `@WebAdapter`, `@PersistenceAdapter`, `@UseCase`: Kotlin
  classes need the `kotlin-spring` plugin (it auto-opens classes annotated
  with these). Confirm the plugin is added in sprint-00 before any class
  gets converted, or the bean container will fail to subclass for AOP.
- For JPA entities under `adapter/out/persistence/*`: enable the
  `kotlin-jpa` plugin so all-open + no-arg synthesizes the required
  default constructor. Without it, Hibernate cannot instantiate entities.
- `application.properties` keys and Spring Boot autoconfig stay unchanged.

## 4. Behavioral landmines (concrete pitfalls)

1. **Money equality.** Lombok `@Value` on `Money` (wrapping `BigDecimal`)
   compared by `compareTo` semantics in practice (because the test compares
   instances generated identically). A naive `data class Money(val amount:
   BigDecimal)` uses `BigDecimal.equals`, which is scale-sensitive
   (`10.0 != 10.00`). Either:
   - Normalize scale in the constructor (`amount.setScale(2, HALF_EVEN)`),
     or
   - Override `equals` to compare via `compareTo`.
   Confirm against `MoneyTest` before declaring sprint-02 done.
2. **`SendMoneyCommand` validation.** `SelfValidating` runs Bean Validation
   on construction. With Kotlin, `init { validateSelf() }` in the
   `data class` body works; ensure the `@NotNull`-style constraints carry
   over and the `ConstraintViolationException` test still triggers.
3. **`Optional<AccountId>` at the port boundary.** Convert to `AccountId?`
   only when the port file itself is in scope (sprint-03). Converting it
   earlier breaks Java callers in not-yet-converted layers.
4. **Jackson serialization shape.** `Money`/`Activity` are serialized in
   the web layer. After conversion, hit
   `POST /accounts/send/{src}/{dst}/{amount}` and diff the response body
   against a captured baseline. Field name/casing must match exactly.
5. **ArchUnit `.java` scanning.** Some rules scan Java sources by file
   suffix. Confirm they don't silently pass once the `.java` files vanish
   — re-target to package-based scanning if needed (sprint-09).

## 5. Verification recipes

```
# Build / test the converted code
./gradlew compileKotlin compileTestKotlin
./gradlew test
./gradlew check                            # picks up ArchUnit

# Confirm Lombok is gone within scope
grep -R "import lombok"  src/main/kotlin src/test/kotlin   # expect empty
grep -R "lombok"         src/main/kotlin src/test/kotlin   # double-check

# Spot-check Kotlin idioms within converted files
grep -Rn "lateinit var"    src/main/kotlin
grep -Rn "@Autowired"      src/main/kotlin
grep -Rn "Optional<"       src/main/kotlin
grep -Rn "!!\b"            src/main/kotlin   # each must have a justifying comment

# Package layout must match
diff <(find src/main/java   -type d | sed 's|src/main/java/||'   | sort) \
     <(find src/main/kotlin -type d | sed 's|src/main/kotlin/||' | sort)
```

## 6. When converting

- One sprint = one logical layer or small file cluster. Don't bleed scope.
- Move `Foo.java` → `Foo.kt` (same package). Delete the `.java` only after
  `./gradlew compileKotlin compileTestKotlin test` is green.
- Never modify a test to make it pass. If the test fails, the conversion
  is wrong.
- Don't widen visibility. `package-private` → `internal`.
- Leave `// TODO(kotlin-migration):` markers for anything you defer to a
  later sprint; never silently change behavior.

Refer to `.claude/harness/criteria/kotlin-conversion.md` for the scoring
rubric the Evaluator will apply.
