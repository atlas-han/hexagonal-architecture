package io.reflectoring.buckpal.account.application.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reflectoring.buckpal.account.application.port.`in`.SendMoneyCommand
import io.reflectoring.buckpal.account.application.port.out.AccountLock
import io.reflectoring.buckpal.account.application.port.out.LoadAccountPort
import io.reflectoring.buckpal.account.application.port.out.UpdateAccountStatePort
import io.reflectoring.buckpal.account.domain.Account
import io.reflectoring.buckpal.account.domain.Account.AccountId
import io.reflectoring.buckpal.account.domain.Money

class SendMoneyServiceTest : BehaviorSpec({

    // Each leaf re-runs the spec lambda, so every leaf gets a fresh mock
    // graph — no shared mutable state, no clearMocks() gymnastics needed.
    // `relaxUnitFun = true` is applied to the two collaborators whose mocked
    // methods all return `Unit` (lockAccount / releaseAccount /
    // updateActivities) so we never have to stub a no-op return; everything
    // else stays strict.
    fun moneyTransferProperties(): MoneyTransferProperties =
        MoneyTransferProperties(Money.of(Long.MAX_VALUE))

    fun givenAnAccountWithId(
        loadAccountPort: LoadAccountPort,
        id: AccountId,
    ): Account {
        val account = mockk<Account>()
        every { account.id } returns id
        every { loadAccountPort.loadAccount(id, any()) } returns account
        return account
    }

    fun givenWithdrawalWillFail(account: Account) {
        every { account.withdraw(any(), any()) } returns false
    }

    fun givenWithdrawalWillSucceed(account: Account) {
        every { account.withdraw(any(), any()) } returns true
    }

    fun givenDepositWillSucceed(account: Account) {
        every { account.deposit(any(), any()) } returns true
    }

    fun thenAccountsHaveBeenUpdated(
        updateAccountStatePort: UpdateAccountStatePort,
        vararg accountIds: AccountId,
    ) {
        // mutableListOf<Account>() + capture(list) is the spec-blessed pattern
        // for "multiple captures"; a single slot<Account>() would only retain
        // the last invocation's argument.
        val captured = mutableListOf<Account>()
        verify(exactly = accountIds.size) {
            updateAccountStatePort.updateActivities(capture(captured))
        }
        val updatedAccountIds = captured.map { it.id }
        updatedAccountIds shouldContainAll accountIds.toList()
    }

    given("the source account's withdrawal will fail") {
        `when`("sending money") {
            then("only the source account is locked and released") {
                val loadAccountPort = mockk<LoadAccountPort>()
                val accountLock = mockk<AccountLock>(relaxUnitFun = true)
                val updateAccountStatePort = mockk<UpdateAccountStatePort>(relaxUnitFun = true)
                val sendMoneyService = SendMoneyService(
                    loadAccountPort,
                    accountLock,
                    updateAccountStatePort,
                    moneyTransferProperties(),
                )

                val sourceAccountId = AccountId(41L)
                val sourceAccount = givenAnAccountWithId(loadAccountPort, sourceAccountId)

                val targetAccountId = AccountId(42L)
                val targetAccount = givenAnAccountWithId(loadAccountPort, targetAccountId)

                givenWithdrawalWillFail(sourceAccount)
                givenDepositWillSucceed(targetAccount)

                val command = SendMoneyCommand(
                    sourceAccountId,
                    targetAccountId,
                    Money.of(300L),
                )

                val success = sendMoneyService.sendMoney(command)

                success shouldBe false

                verify { accountLock.lockAccount(sourceAccountId) }
                verify { accountLock.releaseAccount(sourceAccountId) }
                verify(exactly = 0) { accountLock.lockAccount(targetAccountId) }
            }
        }
    }

    given("a source and target account both ready to transact") {
        `when`("sending money") {
            then("the transaction succeeds and both accounts are locked, mutated, released, and persisted") {
                val loadAccountPort = mockk<LoadAccountPort>()
                val accountLock = mockk<AccountLock>(relaxUnitFun = true)
                val updateAccountStatePort = mockk<UpdateAccountStatePort>(relaxUnitFun = true)
                val sendMoneyService = SendMoneyService(
                    loadAccountPort,
                    accountLock,
                    updateAccountStatePort,
                    moneyTransferProperties(),
                )

                val sourceAccountId = AccountId(41L)
                val sourceAccount = givenAnAccountWithId(loadAccountPort, sourceAccountId)

                val targetAccountId = AccountId(42L)
                val targetAccount = givenAnAccountWithId(loadAccountPort, targetAccountId)

                givenWithdrawalWillSucceed(sourceAccount)
                givenDepositWillSucceed(targetAccount)

                val money = Money.of(500L)

                val command = SendMoneyCommand(
                    sourceAccountId,
                    targetAccountId,
                    money,
                )

                val success = sendMoneyService.sendMoney(command)

                success shouldBe true

                verify { accountLock.lockAccount(sourceAccountId) }
                verify { sourceAccount.withdraw(money, targetAccountId) }
                verify { accountLock.releaseAccount(sourceAccountId) }

                verify { accountLock.lockAccount(targetAccountId) }
                verify { targetAccount.deposit(money, sourceAccountId) }
                verify { accountLock.releaseAccount(targetAccountId) }

                thenAccountsHaveBeenUpdated(
                    updateAccountStatePort,
                    sourceAccountId,
                    targetAccountId,
                )
            }
        }
    }

    given("a command whose amount exceeds the maximum transfer threshold") {
        `when`("sending money") {
            then("a ThresholdExceededException is thrown and no account is loaded") {
                val loadAccountPort = mockk<LoadAccountPort>()
                val accountLock = mockk<AccountLock>(relaxUnitFun = true)
                val updateAccountStatePort = mockk<UpdateAccountStatePort>(relaxUnitFun = true)
                val sendMoneyService = SendMoneyService(
                    loadAccountPort,
                    accountLock,
                    updateAccountStatePort,
                    MoneyTransferProperties(Money.of(100L)),
                )

                val command = SendMoneyCommand(
                    AccountId(41L),
                    AccountId(42L),
                    Money.of(500L),
                )

                shouldThrow<ThresholdExceededException> {
                    sendMoneyService.sendMoney(command)
                }

                verify(exactly = 0) { loadAccountPort.loadAccount(any(), any()) }
                verify(exactly = 0) { accountLock.lockAccount(any()) }
                verify(exactly = 0) { accountLock.releaseAccount(any()) }
                verify(exactly = 0) { updateAccountStatePort.updateActivities(any()) }
            }
        }
    }

    given("a withdrawal that succeeds but a deposit that fails") {
        `when`("sending money") {
            then("the result is false, both locks are acquired and released, and no activities are persisted") {
                val loadAccountPort = mockk<LoadAccountPort>()
                val accountLock = mockk<AccountLock>(relaxUnitFun = true)
                val updateAccountStatePort = mockk<UpdateAccountStatePort>(relaxUnitFun = true)
                val sendMoneyService = SendMoneyService(
                    loadAccountPort,
                    accountLock,
                    updateAccountStatePort,
                    moneyTransferProperties(),
                )

                val sourceAccountId = AccountId(41L)
                val sourceAccount = givenAnAccountWithId(loadAccountPort, sourceAccountId)

                val targetAccountId = AccountId(42L)
                val targetAccount = givenAnAccountWithId(loadAccountPort, targetAccountId)

                givenWithdrawalWillSucceed(sourceAccount)
                every { targetAccount.deposit(any(), any()) } returns false

                val money = Money.of(500L)
                val command = SendMoneyCommand(
                    sourceAccountId,
                    targetAccountId,
                    money,
                )

                val success = sendMoneyService.sendMoney(command)

                success shouldBe false

                verify { accountLock.lockAccount(sourceAccountId) }
                verify { accountLock.lockAccount(targetAccountId) }
                verify { accountLock.releaseAccount(sourceAccountId) }
                verify { accountLock.releaseAccount(targetAccountId) }
                verify(exactly = 0) { updateAccountStatePort.updateActivities(any()) }
            }
        }
    }
})
