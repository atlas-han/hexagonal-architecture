package io.reflectoring.buckpal.account.application.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reflectoring.buckpal.account.application.port.out.LoadAccountPort
import io.reflectoring.buckpal.account.domain.Account
import io.reflectoring.buckpal.account.domain.Account.AccountId
import io.reflectoring.buckpal.account.domain.BaselineDate
import io.reflectoring.buckpal.account.domain.Money

class GetAccountBalanceServiceTest : BehaviorSpec({

    // Hand-rolled test double for LoadAccountPort, used in lieu of a mockk
    // matcher on the `BaselineDate` argument: mockk 1.13.8 cannot bind a
    // matcher slot to a `@JvmInline value class` parameter at signature-
    // detection time (the JVM ABI passes the unboxed `LocalDateTime` while
    // mockk's matcher tracks the boxed `BaselineDate`, and the mismatch
    // produces "Failed matching mocking signature ... left matchers: [...]").
    // Once mockk gains first-class inline-value-class matchers, this can
    // collapse back to a single `mockk<LoadAccountPort>()` + `any<BaselineDate>()`.
    class RecordingLoadAccountPort(private val account: Account) : LoadAccountPort {
        var lastAccountId: AccountId? = null
        var lastBaselineDate: BaselineDate? = null
        var callCount: Int = 0

        override fun loadAccount(accountId: AccountId, baselineDate: BaselineDate): Account {
            lastAccountId = accountId
            lastBaselineDate = baselineDate
            callCount += 1
            return account
        }
    }

    given("an account with a known balance") {
        `when`("getAccountBalance is queried") {
            then("it returns the account's calculated balance") {
                val accountId = AccountId(42L)
                val expectedBalance = Money.of(1234L)
                val account = mockk<Account>()
                every { account.calculateBalance() } returns expectedBalance

                val loadAccountPort = RecordingLoadAccountPort(account)
                val service = GetAccountBalanceService(loadAccountPort)

                val actualBalance = service.getAccountBalance(accountId)

                actualBalance shouldBe expectedBalance
                loadAccountPort.callCount shouldBe 1
                loadAccountPort.lastAccountId shouldBe accountId
                // The service builds the baseline via BaselineDate.now(); we
                // confirm the wrapper-typed value was actually threaded
                // through, without pinning the exact instant.
                (loadAccountPort.lastBaselineDate != null) shouldBe true

                verify { account.calculateBalance() }
            }
        }
    }
})
