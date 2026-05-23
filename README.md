# BuckPal — Hexagonal Architecture Example

A worked example of the **Hexagonal (Ports & Adapters) Architecture** applied to a
small money-transfer service. Originally companion code to the book
[Get Your Hands Dirty on Clean Architecture](https://leanpub.com/get-your-hands-dirty-on-clean-architecture)
by Tom Hombergs; the code in this repository has since been migrated from Java to
**100% Kotlin** while keeping the same architectural shape.

The interesting parts are the *package layout* and the *dependency rules* — the
business logic itself (`SendMoney`) is intentionally tiny so the architecture stays
the focus.

## Tech Stack

| | |
| --- | --- |
| Language | Kotlin 1.6.21 (JVM 11 bytecode) |
| Framework | Spring Boot 2.4.3 — Web, Validation, Data JPA |
| Persistence | H2 in-memory (dev + tests) |
| Testing | JUnit 5 platform · [Kotest](https://kotest.io/) 5.5.5 · [MockK](https://mockk.io/) 1.13.8 + SpringMockK |
| Architecture tests | [ArchUnit](https://www.archunit.org/) 0.16.0 |
| Coverage | JaCoCo 0.8.10 — build fails below 90 % project-wide instruction coverage (currently ≈ 97.5 %) |
| Build | Gradle (wrapper included) |

## Prerequisites

- JDK 11 or newer
- Nothing else — the Gradle wrapper handles Gradle/Kotlin/Spring versions.

## Build & Run

```bash
./gradlew build                 # compile, run all tests, verify coverage
./gradlew bootRun               # start the Spring Boot app on :8080
./gradlew test                  # run unit + integration + ArchUnit tests
./gradlew jacocoTestReport      # HTML report at build/reports/jacoco/test/html
./gradlew jacocoTestCoverageVerification   # enforce ≥ 90 % coverage rule
./gradlew check                 # test + verification + lint
```

`./gradlew bootRun` boots an in-memory H2 database; no external services are
required.

### Try the API

The single inbound HTTP endpoint transfers money between two accounts:

```
POST /accounts/send/{sourceAccountId}/{targetAccountId}/{amount}
```

`amount` is parsed as a plain integer (the domain uses `BigInteger` units, not
fractional currency). The maximum allowed transfer is bound from
`application.yml`:

```yaml
buckpal:
  transferThreshold: 10000
```

Transfers exceeding the threshold raise `ThresholdExceededException`.

## Architectural Overview

The single bounded context is `account/`. Inside it the code is split into
three layers — **domain**, **application**, **adapter** — with strict
dependency direction enforced by ArchUnit (see *Architecture tests* below).

```
io.reflectoring.buckpal
├── BuckPalApplication.kt                        # Spring Boot entry point
├── BuckPalConfiguration.kt                      # @Configuration beans
├── BuckPalConfigurationProperties.kt            # @ConfigurationProperties
│
├── common/                                      # cross-cutting stereotypes
│   ├── UseCase.kt                ── @UseCase             (= @Component)
│   ├── WebAdapter.kt             ── @WebAdapter          (= @Component)
│   ├── PersistenceAdapter.kt     ── @PersistenceAdapter  (= @Component)
│   └── SelfValidating.kt         ── Bean-validation base class
│
└── account/                                     # bounded context
    │
    ├── domain/                                  # pure Kotlin, no Spring
    │   ├── Account.kt          ── aggregate root (factory: withId / withoutId)
    │   ├── Activity.kt         ── single money-movement event
    │   ├── ActivityWindow.kt   ── windowed list of activities
    │   └── Money.kt            ── value object (BigInteger amount)
    │
    ├── application/
    │   ├── port/in/            ── inbound ports (driven from the outside)
    │   │   ├── SendMoneyUseCase.kt
    │   │   ├── SendMoneyCommand.kt        (self-validating)
    │   │   └── GetAccountBalanceQuery.kt
    │   │
    │   ├── port/out/           ── outbound ports (drive the outside)
    │   │   ├── LoadAccountPort.kt
    │   │   ├── UpdateAccountStatePort.kt
    │   │   └── AccountLock.kt
    │   │
    │   └── service/            ── use-case implementations (@UseCase)
    │       ├── SendMoneyService.kt
    │       ├── GetAccountBalanceService.kt
    │       ├── MoneyTransferProperties.kt
    │       ├── NoOpAccountLock.kt
    │       └── ThresholdExceededException.kt
    │
    └── adapter/
        ├── in/web/             ── driving adapter (@WebAdapter)
        │   └── SendMoneyController.kt
        │
        └── out/persistence/    ── driven adapter (@PersistenceAdapter)
            ├── AccountPersistenceAdapter.kt
            ├── AccountMapper.kt
            ├── SpringDataAccountRepository.kt
            ├── ActivityRepository.kt
            ├── AccountJpaEntity.kt
            └── ActivityJpaEntity.kt
```

### Dependency direction

```
   adapter.in.web ──▶  application.port.in ──▶  application.service ──▶  domain
                                                          │
                                                          ▼
                       application.port.out  ◀── adapter.out.persistence
```

- The **domain** depends on nothing else in the project.
- The **application** layer depends only on the domain; outside contracts are
  declared as `port.in` / `port.out` interfaces.
- **Adapters** depend on the application (they implement `port.out` or call
  `port.in`) — never the other way around.

### Walk-through: `SendMoney`

1. `SendMoneyController` (`@WebAdapter`) receives `POST /accounts/send/...`,
   builds a `SendMoneyCommand`, and invokes `SendMoneyUseCase`.
2. `SendMoneyService` (`@UseCase`, `@Transactional`):
   - validates against `MoneyTransferProperties.maximumTransferThreshold`,
   - loads both accounts through `LoadAccountPort`,
   - locks each account with `AccountLock`,
   - calls `Account.withdraw(...)` / `Account.deposit(...)` (domain logic),
   - persists changes through `UpdateAccountStatePort`.
3. `AccountPersistenceAdapter` (`@PersistenceAdapter`) implements both outbound
   ports against Spring Data JPA + H2.

`SendMoneyService.kt` is a good entry point if you want to read the code.

## Tests

The test tree mirrors the production package layout, plus two architectural-rule
test suites at the root:

```
src/test/kotlin/io/reflectoring/buckpal
├── account/…                       # unit + integration tests for domain, services, adapters
├── archunit/                       # reusable ArchUnit DSL for hexagonal layering
│   ├── HexagonalArchitecture.kt
│   ├── ApplicationLayer.kt
│   ├── Adapters.kt
│   └── ArchitectureElement.kt
├── common/                         # test helpers (AccountTestData, ActivityTestData, …)
├── DependencyRuleTests.kt          # enforces the hexagonal dependency direction
├── SendMoneySystemTest.kt          # end-to-end @SpringBootTest
└── BuckPalApplicationTests.kt      # context-load smoke test
```

### Architecture tests

`DependencyRuleTests` declares the hexagonal layout against the `account`
package and lets ArchUnit fail the build if any dependency crosses a layer the
wrong way:

```kotlin
HexagonalArchitecture.boundedContext("io.reflectoring.buckpal.account")
    .withDomainLayer("domain")
    .withAdaptersLayer("adapter")
        .incoming("in.web")
        .outgoing("out.persistence")
    .and()
    .withApplicationLayer("application")
        .services("service")
        .incomingPorts("port.in")
        .outgoingPorts("port.out")
    .and()
    .withConfiguration("configuration")
    .check(ClassFileImporter().importPackages("io.reflectoring.buckpal.."))
```

These are plain JVM tests — they run with the rest of `./gradlew test`.

### Coverage

`jacocoTestCoverageVerification` runs as part of `./gradlew check` and fails
the build below **90 % project-wide instruction coverage**. Excluded from the
count are the Spring entry-point classes (`BuckPalApplication`,
`BuckPalConfiguration`) and the JPA entities — see the `afterEvaluate { … }`
block in `build.gradle`.

The current state of `main` sits at roughly **97.5 % instruction coverage**,
with every class also above the 90 % floor.

## Further Reading

- Book: [Get Your Hands Dirty on Clean Architecture](https://leanpub.com/get-your-hands-dirty-on-clean-architecture) — Tom Hombergs
- Article: [Hexagonal Architecture with Java and Spring](https://reflectoring.io/spring-hexagonal/)
- Article: [Building a Multi-Module Spring Boot Application with Gradle](https://reflectoring.io/spring-boot-gradle-multi-module/)

## License

This project follows the license of the upstream
[`thombergs/buckpal`](https://github.com/thombergs/buckpal) repository.
