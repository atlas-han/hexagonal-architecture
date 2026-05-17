# Sprint 5 Handoff — `account/adapter/in/web/`

**Generator:** main session
**Result:** SELF-CHECK GREEN — awaiting Evaluator Phase B verification.

## What changed

- `SendMoneyController.java` → `SendMoneyController.kt`. Single 1:1 conversion.
  `@WebAdapter @RestController internal class` with primary-constructor injection of
  `SendMoneyUseCase`. Explicit `@PathVariable("...")` on each of 3 path params.
  `POST /accounts/send/{sourceAccountId}/{targetAccountId}/{amount}` path string
  preserved verbatim.

## Contract checklist

- [x] `find src/main/java/io/reflectoring/buckpal/account/adapter/in -name '*.java'` → 0 ✓
- [x] `find src/main/kotlin/io/reflectoring/buckpal/account/adapter/in -name '*.kt'` → 1 ✓
- [x] `grep -R "import lombok"` in adapter/in/ → empty ✓
- [x] anti-pattern grep `(!!|lateinit|Optional<|@Autowired)` → 0 ✓
- [x] `grep "@WebAdapter"` → 1 ✓
- [x] `grep "@RestController"` → 1 ✓
- [x] Path string preserved verbatim ✓
- [x] `@PathVariable` count → 3 ✓ (explicit names)
- [x] `./gradlew clean compileKotlin compileJava compileTestKotlin compileTestJava test` → BUILD SUCCESSFUL, 16/16 pass ✓
- [x] `./gradlew test --tests SendMoneyControllerTest` → 1/1 pass ✓
- [x] `./gradlew test --tests SendMoneySystemTest` → 1/1 pass ✓ (full HTTP path)
- [x] kotlinc warnings → 0 ✓

All 12 mechanical checks PASS.

## Idiomatic Kotlin choices worth flagging

1. **`in` package escaping.** Both `adapter.`in` and the imported
   `port.`in` segments are backtick-escaped because `in` is a Kotlin
   hard keyword. Three lines in the file carry backticks (the package
   declaration plus two `port.in.*` imports). Java FQN remains unescaped.
2. **`internal class`** matches the Java package-private original.
   `@RestController` + Spring component-scan picks it up — verified by
   `SendMoneySystemTest` green (the test actually POSTs to the
   endpoint).
3. **`fun sendMoney(...)` returns `Unit`** (no return type annotation
   needed), serialized by Spring MVC as no body — same as the original
   `void`.
4. **Direct property access** on `SendMoneyCommand` is not used here
   (the controller constructs the command), but the controller's
   `sendMoneyUseCase` is injected via primary constructor as a
   `private val` — no `@Autowired`.

## Anything the Evaluator should pay extra attention to

1. **`SendMoneySystemTest` is the strongest signal** for this sprint —
   it does a real `mvc.perform(post(...))` against the live Spring
   context and exercises the controller → SendMoneyService → JPA
   adapter → H2 vertical slice. Confirms the new Kotlin controller is
   correctly bound to the URL pattern and that path-variable binding
   works.
2. **`SendMoneyControllerTest` is a `@WebMvcTest`-style slice test**
   that mocks the use case. It also passes.

## TODOs deferred to later sprints

- None for this sprint scope.

## Commit

Not yet committed. Generator will commit after Evaluator PASS.
