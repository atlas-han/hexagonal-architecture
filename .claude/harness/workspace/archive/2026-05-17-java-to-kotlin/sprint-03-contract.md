STATUS: AGREED
# Sprint 3 Contract — `account/application/port/`

**Status:** DRAFT (awaiting Evaluator review)
**Generator:** main session
**Sprint goal (from spec):** Convert the 6 port files (3 in, 3 out).
Interfaces convert 1:1. `SendMoneyCommand` is the only non-interface and
requires careful Bean Validation + `SelfValidating` interop.

## Files in scope

Production (6 .java → 6 .kt):
- `port/in/SendMoneyUseCase.java` → delete + `.kt` create
- `port/in/SendMoneyCommand.java` → delete + `.kt` create
- `port/in/GetAccountBalanceQuery.java` → delete + `.kt` create
- `port/out/LoadAccountPort.java` → delete + `.kt` create
- `port/out/UpdateAccountStatePort.java` → delete + `.kt` create
- `port/out/AccountLock.java` → delete + `.kt` create

Nothing else is touched.

## Conversion targets

| Java file | Kotlin equivalent |
|-----------|-------------------|
| `SendMoneyUseCase.java` | `interface SendMoneyUseCase { fun sendMoney(command: SendMoneyCommand): Boolean }` |
| `GetAccountBalanceQuery.java` | `interface GetAccountBalanceQuery { fun getAccountBalance(accountId: Account.AccountId): Money }` |
| `LoadAccountPort.java` | `interface LoadAccountPort { fun loadAccount(accountId: Account.AccountId, baselineDate: LocalDateTime): Account }` |
| `UpdateAccountStatePort.java` | `interface UpdateAccountStatePort { fun updateActivities(account: Account) }` |
| `AccountLock.java` | `interface AccountLock { fun lockAccount(accountId: Account.AccountId); fun releaseAccount(accountId: Account.AccountId) }` |
| `SendMoneyCommand.java` | `class SendMoneyCommand(...) : SelfValidating<SendMoneyCommand>()` with `@field:NotNull` per field and `init { validateSelf() }` | // EVALUATOR: this row says `class`, but the final decision below (line 112) and product-spec.md §3 both mandate `data class`. Generator must use `data class` — update this row for consistency.

## Acceptance checks

- [ ] `find src/main/java/io/reflectoring/buckpal/account/application/port -name '*.java'` → 0
- [ ] `find src/main/kotlin/io/reflectoring/buckpal/account/application/port -name '*.kt'` → 6
- [ ] `grep -R "import lombok" src/main/kotlin/io/reflectoring/buckpal/account/application/port` → empty
- [ ] `grep -R "@field:NotNull" src/main/kotlin/io/reflectoring/buckpal/account/application/port/in/SendMoneyCommand.kt | wc -l` → 3 (sourceAccountId, targetAccountId, money — each needs the site target so Bean Validation reads the annotation on the JVM field, not the parameter or property getter)
- [ ] `grep "SelfValidating<SendMoneyCommand>" src/main/kotlin/io/reflectoring/buckpal/account/application/port/in/SendMoneyCommand.kt` → 1 match
- [ ] `grep "validateSelf()" src/main/kotlin/io/reflectoring/buckpal/account/application/port/in/SendMoneyCommand.kt` → 1 match (in `init` block)
- [ ] `JAVA_HOME=… ./gradlew clean compileKotlin compileJava compileTestKotlin compileTestJava` → BUILD SUCCESSFUL
- [ ] `JAVA_HOME=… ./gradlew test` → 16/16 pass
- [ ] `JAVA_HOME=… ./gradlew test --tests "io.reflectoring.buckpal.account.application.service.SendMoneyServiceTest"` → pass (exercises SendMoneyCommand construction)
- [ ] `JAVA_HOME=… ./gradlew test --tests "io.reflectoring.buckpal.account.adapter.in.web.SendMoneyControllerTest"` → pass (constructs SendMoneyCommand via controller path)
- [ ] `JAVA_HOME=… ./gradlew test --tests "io.reflectoring.buckpal.DependencyRuleTests"` → pass
- [ ] kotlinc warnings on the 6 new files → 0
- [ ] `grep -E "(!!|lateinit|Optional<|@Autowired)" src/main/kotlin/io/reflectoring/buckpal/account/application/port -r` → empty (Idiomatic Kotlin static probe)
- [ ] `grep "^data class SendMoneyCommand" src/main/kotlin/io/reflectoring/buckpal/account/application/port/in/SendMoneyCommand.kt` → 1 match (enforces the `data class` decision)

## Idiomatic Kotlin commitments

### SendMoneyUseCase, GetAccountBalanceQuery, LoadAccountPort, UpdateAccountStatePort, AccountLock

Standard `interface` translation. Method names, parameter types, return
types all preserved 1:1. No method bodies. No `@JvmDefault` needed —
nothing relies on default methods.

### SendMoneyCommand

The original Java:

```java
@Value
@EqualsAndHashCode(callSuper = false)
public class SendMoneyCommand extends SelfValidating<SendMoneyCommand> {
    @NotNull private final AccountId sourceAccountId;
    @NotNull private final AccountId targetAccountId;
    @NotNull private final Money money;

    public SendMoneyCommand(AccountId sourceAccountId, AccountId targetAccountId, Money money) {
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.money = money;
        this.validateSelf();
    }
}
```

Kotlin equivalent:

```kotlin
// EVALUATOR: the snippet below shows `class` with hand-rolled
// equals/hashCode/toString, contradicting the "Final decision" on line 112
// which selects `data class`. Use `data class` — it auto-generates
// field-only equals/hashCode/toString (semantically equivalent to
// @EqualsAndHashCode(callSuper = false) since SelfValidating has no
// equals/hashCode override). The hand-rolled overrides must be dropped.
data class SendMoneyCommand(
    @field:NotNull val sourceAccountId: Account.AccountId,
    @field:NotNull val targetAccountId: Account.AccountId,
    @field:NotNull val money: Money,
) : SelfValidating<SendMoneyCommand>() {

    init {
        validateSelf()
    }
}
```

Key design choices:

1. **`data class` (final).** `SelfValidating<T>` does not override
   `equals`/`hashCode`/`toString` (verified — see
   `src/main/kotlin/io/reflectoring/buckpal/common/SelfValidating.kt`),
   so a Kotlin `data class` extending it produces field-only
   `equals`/`hashCode` — semantically equivalent to Lombok's
   `@EqualsAndHashCode(callSuper = false)`. Kotlin 1.1+ permits `data
   class` to extend a non-data abstract parent; confirmed on Kotlin
   1.6.21 (build.gradle line 4). product-spec.md §3 also explicitly
   lists `SendMoneyCommand` among the value-bearing types that should be
   `data class`.
   - **Final decision: `data class SendMoneyCommand(...) : SelfValidating<SendMoneyCommand>()`.**
   - Do **not** hand-roll `equals`/`hashCode`/`toString` — the compiler-
     synthesized versions are exactly what we want, and writing them by
     hand risks drift.
2. **`@field:NotNull`** site target on each property so the annotation
   lands on the synthesized JVM field. Bean Validation's
   `validator.validate(this)` reads annotations on fields (per the
   project's default config). Without the site target, the annotation
   sits on the constructor parameter and is invisible to Bean Validation
   reflection. Required: `import javax.validation.constraints.NotNull`.
3. **`init { validateSelf() }`** calls the inherited
   `SelfValidating.validateSelf()` immediately after primary-constructor
   property initialization. Mirrors the original `this.validateSelf()`
   placement at the end of the Java ctor.
4. **No additional secondary constructors.** Java's class had only one
   ctor.

## Out of scope

- Any caller of these ports under `application/service/` (Sprint 4),
  `adapter/in/web/` (Sprint 5), or `adapter/out/persistence/` (Sprint 6).
- Tests (Sprint 8).
- Removing the `Optional` shim from `Account.getId()` — still deferred
  to Sprint 4.

## Implementation order

1. Create the 6 `.kt` files. Compile-check with `./gradlew compileKotlin`.
2. Delete the 6 `.java` files.
3. Run `./gradlew compileJava compileTestJava` to confirm Java callers
   (SendMoneyService, SendMoneyController, AccountPersistenceAdapter,
   NoOpAccountLock, MoneyTransferProperties indirectly, plus the tests)
   all still compile.
4. Run the full self-check: `./gradlew clean compileKotlin compileJava
   compileTestKotlin compileTestJava test`.
5. Targeted: `SendMoneyServiceTest`, `SendMoneyControllerTest`,
   `DependencyRuleTests`.
6. Write handoff.

## Risks specific to this sprint

1. **`@field:NotNull` site-target.** Without it, Bean Validation does
   not see the annotation and `validateSelf()` silently passes — which
   would be a *latent* behavior break (no test currently exercises a
   null path to catch this). Detection: the contract's `@field:NotNull`
   grep (≥3 hits) is the only static signal we have. If it ever drops to
   0, the validation is broken.
2. **`data class` with non-data superclass.** Kotlin 1.6.21 supports
   this and synthesizes `equals`/`hashCode`/`toString`/`copy` based only
   on the data-class's own properties. Compatible with
   `@EqualsAndHashCode(callSuper = false)` semantics. Verify by
   `javap -p SendMoneyCommand.class` having `equals(Object)`,
   `hashCode()`, `toString()` declared on the class itself (not just
   inherited).
3. **`SelfValidating<T>` requires the no-arg constructor.** Already true
   — `SelfValidating.kt` (Sprint 1) has `public constructor()` by
   default. ✓
4. **Java callers** (`SendMoneyController.java`,
   `SendMoneyService.java`, test files) reference these ports by FQN.
   No package change → no breakage. Verify via `compileJava`.
5. **Bean Validation in tests.** Neither the unit test nor the
   controller test currently constructs `SendMoneyCommand` with a null
   field, so the `@field:NotNull` path is not test-exercised. That's a
   pre-existing test gap — out of scope here.

## Self-check (Generator, before writing handoff)

- [ ] All 6 `.kt` files compile.
- [ ] All 6 `.java` files in scope deleted.
- [ ] `./gradlew compileJava compileTestJava` pass.
- [ ] `./gradlew test` → 16/16 green.
- [ ] No `import lombok` in any new Kotlin file.
- [ ] `@field:NotNull` appears 3 times in SendMoneyCommand.kt.
- [ ] `init { validateSelf() }` in SendMoneyCommand.kt.
- [ ] 0 kotlinc warnings on the 6 new files.
