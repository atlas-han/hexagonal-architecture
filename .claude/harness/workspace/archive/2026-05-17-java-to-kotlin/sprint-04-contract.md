STATUS: AGREED

# Sprint 4 Contract — `account/application/service/`

**Status:** DRAFT (awaiting Evaluator review)
**Generator:** main session
**Sprint goal (from spec):** Convert the 5 service classes.

## Files in scope

Production (5 .java → 5 .kt):
- `application/service/SendMoneyService.java` → delete + `.kt` create
- `application/service/GetAccountBalanceService.java` → delete + `.kt` create
- `application/service/MoneyTransferProperties.java` → delete + `.kt` create
- `application/service/NoOpAccountLock.java` → delete + `.kt` create
- `application/service/ThresholdExceededException.java` → delete + `.kt` create

Nothing else is touched. **In particular, `Account.kt`'s `getId():
Optional<AccountId>` shim is NOT removed in this sprint** — the
`SendMoneyServiceTest.java` (still Java, Sprint 8) mocks `account.getId()`
and consumes the result as `Optional`, so the shim must remain. Removal is
deferred to Sprint 9 (cleanup, post-test-conversion).

## Conversion targets

| Java file | Kotlin equivalent | Visibility |
|-----------|-------------------|------------|
| `SendMoneyService.java` | `@UseCase @Transactional class SendMoneyService(...) : SendMoneyUseCase` | `public` (matches Java `public class`) — prose at lines 105–113 / 253–255 confirms this; the table previously said `internal` by mistake // EVALUATOR: fixed table to match prose decision. Original Java is `public class SendMoneyService`, so Kotlin keeps `public` (default, no modifier). |
| `GetAccountBalanceService.java` | `class GetAccountBalanceService(...) : GetAccountBalanceQuery` | `internal` |
| `MoneyTransferProperties.java` | `data class MoneyTransferProperties @JvmOverloads constructor(var maximumTransferThreshold: Money = Money.of(1_000_000L))` | `public` (matches Java) |
| `NoOpAccountLock.java` | `@Component class NoOpAccountLock : AccountLock { override fun lockAccount(...){}; override fun releaseAccount(...){} }` | `internal` |
| `ThresholdExceededException.java` | `class ThresholdExceededException(threshold: Money, actual: Money) : RuntimeException("...")` | `public` |

## Acceptance checks

- [ ] `find src/main/java/io/reflectoring/buckpal/account/application/service -name '*.java'` → 0
- [ ] `find src/main/kotlin/io/reflectoring/buckpal/account/application/service -name '*.kt'` → 5
- [ ] `grep -R "import lombok" src/main/kotlin/io/reflectoring/buckpal/account/application/service` → empty
- [ ] `grep -E "(!!|lateinit|@Autowired)" src/main/kotlin/io/reflectoring/buckpal/account/application/service -r` → empty (anti-pattern grep — `Optional<` excluded since we read `account.id` instead, never wrap in Optional)
- [ ] `grep -R "Optional" src/main/kotlin/io/reflectoring/buckpal/account/application/service` → empty (no Optional usage in services; we read `account.id` directly as nullable)
- [ ] `grep "?: error(" src/main/kotlin/io/reflectoring/buckpal/account/application/service/SendMoneyService.kt | wc -l` → at least 2 (source + target account id null checks)
- [ ] `grep "@JvmOverloads" src/main/kotlin/io/reflectoring/buckpal/account/application/service/MoneyTransferProperties.kt` → 1 match (preserves both no-arg + 1-arg Java ctor shapes)
- [ ] `JAVA_HOME=… ./gradlew clean compileKotlin compileJava compileTestKotlin compileTestJava` → BUILD SUCCESSFUL
- [ ] `JAVA_HOME=… ./gradlew test` → 16/16 pass
- [ ] `JAVA_HOME=… ./gradlew test --tests "io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest"` → 2/2 pass
- [ ] `JAVA_HOME=… ./gradlew test --tests "io.reflectoring.buckpal.SendMoneySystemTest"` → 1/1 pass (full Spring Boot + H2 integration test of the send-money flow)
- [ ] `JAVA_HOME=… ./gradlew test --tests "io.reflectoring.buckpal.BuckPalApplicationTests"` → pass (Spring context boots)
- [ ] `JAVA_HOME=… ./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` → pass
- [ ] kotlinc warnings on the 5 new files → 0

## Idiomatic Kotlin commitments

### SendMoneyService

```kotlin
@UseCase
@Transactional
internal class SendMoneyService(
    private val loadAccountPort: LoadAccountPort,
    private val accountLock: AccountLock,
    private val updateAccountStatePort: UpdateAccountStatePort,
    private val moneyTransferProperties: MoneyTransferProperties,
) : SendMoneyUseCase {

    override fun sendMoney(command: SendMoneyCommand): Boolean {
        checkThreshold(command)

        val baselineDate = LocalDateTime.now().minusDays(10)

        val sourceAccount = loadAccountPort.loadAccount(command.sourceAccountId, baselineDate)
        val targetAccount = loadAccountPort.loadAccount(command.targetAccountId, baselineDate)

        val sourceAccountId = sourceAccount.id
            ?: error("expected source account ID not to be empty")
        val targetAccountId = targetAccount.id
            ?: error("expected target account ID not to be empty")

        accountLock.lockAccount(sourceAccountId)
        if (!sourceAccount.withdraw(command.money, targetAccountId)) {
            accountLock.releaseAccount(sourceAccountId)
            return false
        }

        accountLock.lockAccount(targetAccountId)
        if (!targetAccount.deposit(command.money, sourceAccountId)) {
            accountLock.releaseAccount(sourceAccountId)
            accountLock.releaseAccount(targetAccountId)
            return false
        }

        updateAccountStatePort.updateActivities(sourceAccount)
        updateAccountStatePort.updateActivities(targetAccount)

        accountLock.releaseAccount(sourceAccountId)
        accountLock.releaseAccount(targetAccountId)
        return true
    }

    private fun checkThreshold(command: SendMoneyCommand) {
        if (command.money.isGreaterThan(moneyTransferProperties.maximumTransferThreshold)) {
            throw ThresholdExceededException(moneyTransferProperties.maximumTransferThreshold, command.money)
        }
    }
}
```

Key design choices:

1. **`internal` visibility.** The original Java class is `public` (no
   modifier on the class itself = public for Java). Wait — the original
   was `public class SendMoneyService`. Let me re-check. Yes, public.
   Kotlin: keep `public` (default) — not `internal`. Spring needs to
   create the bean; `internal` would Kotlin-compile fine but the
   bytecode mangling on `internal` could trip `@ComponentScan` in edge
   cases. **Decision: leave as `public` (Kotlin default, no modifier).**
2. **kotlin-spring plugin** makes `@UseCase` (meta-annotated `@Component`)
   classes `open`-equivalent for CGLIB proxying of `@Transactional`. No
   manual `open`.
3. **Nullable id pattern.** `sourceAccount.id ?: error(...)` replaces
   `sourceAccount.getId().orElseThrow(...)`. The behavioral equivalence
   is: both throw `RuntimeException`-family with the same message; `error()`
   throws `IllegalStateException`, `orElseThrow(() -> new IllegalStateException(...))`
   from the Java was also `IllegalStateException`. ✓
4. **Direct property access** on the Kotlin `SendMoneyCommand` data class:
   `command.sourceAccountId`, `command.targetAccountId`, `command.money`
   (no `command.getSourceAccountId()`).

### GetAccountBalanceService

```kotlin
internal class GetAccountBalanceService(
    private val loadAccountPort: LoadAccountPort,
) : GetAccountBalanceQuery {

    override fun getAccountBalance(accountId: Account.AccountId): Money =
        loadAccountPort.loadAccount(accountId, LocalDateTime.now()).calculateBalance()
}
```

Original Java is package-private (`class` without modifier) → Kotlin
`internal`. Spring's `@ComponentScan` finds `internal` Kotlin classes
fine (they compile to `public final` with mangled `internal` markers
that Spring ignores).

Wait — `internal` Kotlin classes compile to `public final` but with a
synthetic `$module` suffix on **methods**, not the class. The class is
public from JVM POV. Spring component scan works on byte-level class
visibility. **However**, `GetAccountBalanceService` lacks any Spring
stereotype annotation (`@UseCase`/`@Component`/`@Service`/etc.) in the
original Java — it's not wired as a bean automatically. Checking the
original… yes, no annotation. So this service isn't a Spring bean today.
The test must construct it manually. Conversion: just stay package-
private equivalent (`internal`) without adding annotations.

### MoneyTransferProperties

```kotlin
data class MoneyTransferProperties @JvmOverloads constructor(
    var maximumTransferThreshold: Money = Money.of(1_000_000L),
)
```

- `var` (not `val`) — Java `@Data` generates a setter; preserve that
  affordance even though no current caller uses it.
- `@JvmOverloads` synthesizes `MoneyTransferProperties()` AND
  `MoneyTransferProperties(Money)` from Java, mirroring
  `@NoArgsConstructor @AllArgsConstructor`.
- Used by `BuckPalConfiguration.java` (still Java this sprint) and by
  the unit test:
  - `BuckPalConfiguration.java:18`: `new MoneyTransferProperties(Money.of(...))`
  - `SendMoneyServiceTest.java`: `new MoneyTransferProperties(Money.of(Long.MAX_VALUE))`
  Both 1-arg, both supported by the primary ctor.

### NoOpAccountLock

```kotlin
@Component
internal class NoOpAccountLock : AccountLock {

    override fun lockAccount(accountId: Account.AccountId) {
        // do nothing
    }

    override fun releaseAccount(accountId: Account.AccountId) {
        // do nothing
    }
}
```

- `@Component` carries over directly.
- Original was package-private → `internal`. Spring picks it up.

### ThresholdExceededException

```kotlin
class ThresholdExceededException(threshold: Money, actual: Money) :
    RuntimeException(
        "Maximum threshold for transferring money exceeded: tried to transfer $actual but threshold is $threshold!"
    )
```

- Uses Kotlin string template (`$actual`, `$threshold`) — equivalent to
  Java's `String.format("...%s...%s...", actual, threshold)` because both
  call `Money.toString()` on the value. `Money.toString()` is the
  data-class-generated form: `Money(amount=BigInteger)`. **WAIT** —
  the Java `String.format("%s", actual)` would call `actual.toString()`
  which now returns `Money(amount=…)` from the data class. The Java
  `Money.java` with `@Value` also generated a toString of form
  `Money(amount=…)`. **Same output. ✓**

## Out of scope

- Removing `Optional<AccountId>` shim from `Account.kt` — deferred to
  Sprint 9 (cleanup, after test conversion in Sprint 8).
- Adapter or root files.
- Tests.

## Implementation order

1. Create the 5 `.kt` files.
2. Compile-check: `./gradlew compileKotlin`.
3. Delete the 5 `.java` files.
4. Run `./gradlew compileJava compileTestJava` — Java callers
   (`BuckPalConfiguration.java`, `SendMoneyController.java`, tests) must
   still compile against the new Kotlin services.
5. Run the full self-check.
6. Run `SendMoneySystemTest` specifically — it boots the full Spring
   context and POSTs to `/accounts/send/...`. This is the strongest
   integration probe we have until Sprint 7 boots the app standalone.
7. Write handoff.

## Risks specific to this sprint

1. **`kotlin-spring` + `@UseCase` opening.** The kotlin-spring compiler
   plugin marks all classes annotated with Spring stereotype
   meta-annotations as `open`. `@UseCase` carries `@Component` (Sprint 1
   `UseCase.kt`). Verify by `javap` that `SendMoneyService.class` is NOT
   `final` (CGLIB proxy requirement for `@Transactional`).
2. **`@Transactional` proxying.** Spring wraps `@Transactional` methods
   via CGLIB. Final classes break this with a verbose runtime warning;
   tests would still pass (the unit test mocks ports, no real Spring
   context). The `SendMoneySystemTest` would catch a proxy failure —
   make sure it passes.
3. **`SendMoneyServiceTest.java` (Java, still in place) calls
   `command.getSourceAccountId()` etc.** — Kotlin `data class` auto-
   synthesizes Java getters from `val` properties. ✓
4. **`MoneyTransferProperties(Money)` 1-arg ctor used by
   `BuckPalConfiguration.java`.** Without `@JvmOverloads`, the Kotlin
   data class with default value would only expose the all-args ctor as
   the JVM signature; Java would have to write `new
   MoneyTransferProperties(Money.of(...))` — which works (the all-args
   ctor is 1-arg here). But `@JvmOverloads` also synthesizes the no-arg
   ctor, future-proofing for any `new MoneyTransferProperties()` call.
   We include it.
5. **`SendMoneyService.kt` public visibility.** Original Java was
   `public class`. Keep `public` (Kotlin default) — `internal` would
   work too but `public` matches the original 1:1.
6. **Error message wording in `ThresholdExceededException`.** Kotlin
   string template `"Maximum threshold ... $actual but threshold is $threshold!"`
   must produce **byte-identical** output to the Java `String.format`
   call. Verified by inspection: both interpolate via `toString()`, and
   `Money.toString()` is the same data-class-generated form in both
   the original Lombok-generated and the new Kotlin-generated cases.

## Self-check (Generator, before writing handoff)

- [ ] All 5 `.kt` files compile.
- [ ] All 5 `.java` files deleted.
- [ ] `./gradlew compileJava compileTestJava` pass.
- [ ] `./gradlew test` → 16/16 green.
- [ ] `SendMoneySystemTest` specifically green (this is the most
      integration-heavy probe in the suite).
- [ ] `javap -p SendMoneyService.class` shows non-final class.
- [ ] No `import lombok`, no `!!`, no `lateinit`, no `Optional`, no
      `@Autowired` in the 5 new files.
- [ ] 0 kotlinc warnings.
