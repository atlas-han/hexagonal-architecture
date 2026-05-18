# Sprint 3 Handoff — `account/application/port/`

**Generator:** main session
**Result:** SELF-CHECK GREEN — awaiting Evaluator Phase B verification.

## What changed

Java → Kotlin (6 files, 1:1):

- `port/in/SendMoneyUseCase.java` → `.kt`. `interface SendMoneyUseCase { fun sendMoney(...): Boolean }`.
- `port/in/SendMoneyCommand.java` → `.kt`. `data class SendMoneyCommand(...) : SelfValidating<SendMoneyCommand>()` with three `@field:NotNull` properties and `init { validateSelf() }`.
- `port/in/GetAccountBalanceQuery.java` → `.kt`. Single-method interface.
- `port/out/LoadAccountPort.java` → `.kt`. Single-method interface.
- `port/out/UpdateAccountStatePort.java` → `.kt`. Single-method interface.
- `port/out/AccountLock.java` → `.kt`. Two-method interface.

All 6 `.java` files deleted.

## Contract checklist

- [x] `find src/main/java/.../port -name '*.java'` → 0 ✓
- [x] `find src/main/kotlin/.../port -name '*.kt'` → 6 ✓
- [x] `grep -R "import lombok" src/main/kotlin/.../port` → empty ✓
- [x] `grep "@field:NotNull" SendMoneyCommand.kt | wc -l` → 3 ✓
- [x] `grep "SelfValidating<SendMoneyCommand>" SendMoneyCommand.kt` → 1 ✓
- [x] `grep "validateSelf()" SendMoneyCommand.kt` → 1 (in `init` block) ✓
- [x] `./gradlew clean compileKotlin compileJava compileTestKotlin compileTestJava` → BUILD SUCCESSFUL ✓
- [x] `./gradlew test` → 16/16 pass ✓
- [x] `./gradlew test --tests SendMoneyServiceTest` → 2/2 pass ✓
- [x] `./gradlew test --tests SendMoneyControllerTest` → 1/1 pass ✓
- [x] `./gradlew test --tests DependencyRuleTests` → 2/2 pass ✓
- [x] kotlinc warnings on the 6 new files → 0 ✓
- [x] **anti-pattern grep** `(!!|lateinit|Optional<|@Autowired)` in port/ → 0 ✓
- [x] **`grep "^data class SendMoneyCommand"`** → 1 ✓ (enforces evaluator's `data class` mandate)

All 14 mechanical contract checks PASS.

## Idiomatic Kotlin choices worth flagging

1. **`package io.reflectoring.buckpal.account.application.port.`in`** —
   `in` is a Kotlin hard keyword (used in declaration-site variance), so
   the package segment is escaped with backticks. Both files in
   `port/in/` carry this. Import sites and FQN strings (e.g., in
   ArchUnit assertions, in test imports) continue to use unescaped
   `port.in.*` because backticks only apply at declaration. Java callers
   reference the package as `io.reflectoring.buckpal.account.application.port.in.*`
   normally.
2. **`data class SendMoneyCommand(...) : SelfValidating<SendMoneyCommand>()`.**
   Kotlin 1.6.21 permits `data class` to extend an abstract non-data
   parent. The auto-generated `equals`/`hashCode`/`toString` are
   field-only, semantically identical to the original
   `@Value @EqualsAndHashCode(callSuper = false)`. No hand-rolled
   overrides — matches the evaluator's amended contract.
3. **`@field:NotNull` site target** lands the Bean Validation annotation
   on the underlying JVM field (where Hibernate Validator's default
   field-introspection picks it up). Without the site target,
   `validateSelf()` would silently pass on a null because the annotation
   would sit on the property getter or constructor parameter.
4. **`init { validateSelf() }`** mirrors the Java `this.validateSelf()` at
   the end of the constructor. Kotlin runs `init` blocks after primary-
   ctor property assignment, so all 3 fields are set before validation.
5. **All other 5 interfaces use Kotlin's clean `interface` syntax** —
   no `@JvmDefault`, no default methods, no companion object. Direct 1:1
   translations.

## Anything the Evaluator should pay extra attention to

1. **`in` package escaping.** Confirm Kotlin compile succeeds and Java
   tests still resolve `io.reflectoring.buckpal.account.application.port.in.SendMoneyCommand`
   without trouble. `SendMoneyServiceTest` and `SendMoneyControllerTest`
   both import this package; both green. The `port.in` package in
   `DependencyRuleTests` (ArchUnit) likewise references it via the
   `.incomingPorts("port.in")` string — unaffected by Kotlin source
   escaping.
2. **Bean Validation path is statically guarded but not runtime-exercised.**
   No existing test passes null into `SendMoneyCommand` to trip the
   `@NotNull` violation. The `@field:NotNull` grep is the only static
   guarantee; if a future change accidentally drops the site target, no
   green test will fail.
3. **`SendMoneyService.java` still calls `command.getSourceAccountId()`
   etc.** Kotlin `data class` auto-synthesizes `getSourceAccountId()`,
   `getTargetAccountId()`, `getMoney()` (Java-style getters from `val`
   properties) — Java callers continue to compile and run.
4. **No new `!!`, `lateinit`, `Optional<`, or `@Autowired`** introduced.
   Confirmed by the anti-pattern grep (count 0). The `Optional<AccountId>`
   shim is still present in `Account.kt` (Sprint 2) — that's outside
   this sprint's scope.

## TODOs deferred to later sprints

- Sprint 4 (services): rewrite `SendMoneyService` in Kotlin, replacing
  `account.getId().orElseThrow(...)` with `account.id ?: error(...)` and
  removing the `Optional<AccountId>` shim from `Account.kt` once no
  Java caller of `Account.getId()` remains.

## Commit

Not yet committed. Generator will commit after Evaluator PASS.
