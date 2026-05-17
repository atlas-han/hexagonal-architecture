STATUS: AGREED
# Sprint 5 Contract — `account/adapter/in/web/`

**Status:** DRAFT (awaiting Evaluator review)
**Generator:** main session
**Sprint goal (from spec):** Convert the single web adapter.

## Files in scope

Production (1 .java → 1 .kt):
- `account/adapter/in/web/SendMoneyController.java` → delete + `.kt` create

Nothing else.

## Conversion target

| Java file | Kotlin equivalent |
|-----------|-------------------|
| `SendMoneyController.java` | `@WebAdapter @RestController internal class SendMoneyController(private val sendMoneyUseCase: SendMoneyUseCase)` |

Original Java is package-private (`class SendMoneyController` with no
modifier). Kotlin equivalent: `internal`. Spring's `@RestController` +
component-scan picks up `internal` classes fine (bytecode is `public
final` with name mangling on methods only).

## Acceptance checks

- [ ] `find src/main/java/io/reflectoring/buckpal/account/adapter/in -name '*.java'` → 0
- [ ] `find src/main/kotlin/io/reflectoring/buckpal/account/adapter/in -name '*.kt'` → 1
- [ ] `grep -R "import lombok" src/main/kotlin/io/reflectoring/buckpal/account/adapter/in` → empty
- [ ] `grep -E "(!!|lateinit|Optional<|@Autowired)" src/main/kotlin/io/reflectoring/buckpal/account/adapter/in -r` → empty
- [ ] `grep "@WebAdapter" src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt` → 1
- [ ] `grep "@RestController" src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt` → 1
- [ ] `grep '"/accounts/send/{sourceAccountId}/{targetAccountId}/{amount}"' src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt` → 1 (path string preserved verbatim)
- [ ] `grep "@PathVariable" src/main/kotlin/io/reflectoring/buckpal/account/adapter/in/web/SendMoneyController.kt | wc -l` → 3 (each path variable annotated, with explicit "sourceAccountId"/"targetAccountId"/"amount" names — do NOT rely on Kotlin parameter-name reflection)
- [ ] `JAVA_HOME=… ./gradlew clean compileKotlin compileJava compileTestKotlin compileTestJava test` → BUILD SUCCESSFUL, 16/16 pass
- [ ] `JAVA_HOME=… ./gradlew test --tests "io.reflectoring.buckpal.account.adapter.in.web.SendMoneyControllerTest"` → 1/1 pass
- [ ] `JAVA_HOME=… ./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"` → 1/1 pass (full HTTP path through Spring)
- [ ] kotlinc warnings on the new file → 0

## Idiomatic Kotlin commitments

```kotlin
@WebAdapter
@RestController
internal class SendMoneyController(
    private val sendMoneyUseCase: SendMoneyUseCase,
) {

    @PostMapping("/accounts/send/{sourceAccountId}/{targetAccountId}/{amount}")
    fun sendMoney(
        @PathVariable("sourceAccountId") sourceAccountId: Long,
        @PathVariable("targetAccountId") targetAccountId: Long,
        @PathVariable("amount") amount: Long,
    ) {
        val command = SendMoneyCommand(
            Account.AccountId(sourceAccountId),
            Account.AccountId(targetAccountId),
            Money.of(amount),
        )
        sendMoneyUseCase.sendMoney(command)
    }
}
```

Key points:
1. **Primary-constructor injection** — no `@Autowired field`.
2. **`internal`** matches Java package-private. `@RestController` works.
3. **`@PathVariable("name")` is explicit** for each parameter — avoids
   reliance on Kotlin parameter-name reflection (which works in modern
   Spring but is brittle to compiler-flag changes).
4. **`fun sendMoney(...): Unit`** — return type is `Unit` (no explicit
   declaration), matching original `void`. Spring serializes `Unit` as
   no body, same as `void`.
5. **`Money.of(amount)`** — calls the `@JvmStatic` companion fun from
   Sprint 2; Kotlin compiles this to the same JVM call site as the
   original Java.

## Out of scope

- Persistence adapter (Sprint 6).
- Tests (Sprint 8).

## Implementation order

1. Create `SendMoneyController.kt`.
2. Delete `SendMoneyController.java`.
3. Run self-check.
4. Targeted: `SendMoneyControllerTest`, `SendMoneySystemTest`.
5. Write handoff.

## Risks specific to this sprint

1. **`@PathVariable` resolution.** Spring's parameter-name resolution
   works in Kotlin but is fragile. Explicit `@PathVariable("name")` is
   the safe choice — original Java also used explicit names.
2. **`Unit` vs `void` return.** Spring MVC accepts both. The
   `SendMoneySystemTest` confirms HTTP behavior end-to-end.

## Self-check (Generator)

- [ ] All checks above.
