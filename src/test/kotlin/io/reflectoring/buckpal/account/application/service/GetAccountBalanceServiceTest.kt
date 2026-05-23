package io.reflectoring.buckpal.account.application.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reflectoring.buckpal.account.application.port.out.LoadAccountPort
import io.reflectoring.buckpal.account.domain.Account
import io.reflectoring.buckpal.account.domain.Account.AccountId
import io.reflectoring.buckpal.account.domain.Money
import java.time.LocalDateTime

class GetAccountBalanceServiceTest : BehaviorSpec({

    given("an account with a known balance") {
        `when`("getAccountBalance is queried") {
            then("it returns the account's calculated balance") {
                val loadAccountPort = mockk<LoadAccountPort>()
                val service = GetAccountBalanceService(loadAccountPort)

                val accountId = AccountId(42L)
                val expectedBalance = Money.of(1234L)
                val account = mockk<Account>()
                every { account.calculateBalance() } returns expectedBalance
                every { loadAccountPort.loadAccount(eq(accountId), any()) } returns account

                val actualBalance = service.getAccountBalance(accountId)

                actualBalance shouldBe expectedBalance

                verify { loadAccountPort.loadAccount(eq(accountId), any<LocalDateTime>()) }
                verify { account.calculateBalance() }
            }
        }
    }
})
