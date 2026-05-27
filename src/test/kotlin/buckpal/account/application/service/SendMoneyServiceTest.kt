package buckpal.account.application.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import buckpal.account.application.port.`in`.SendMoneyCommand
import buckpal.account.application.port.out.AccountLock
import buckpal.account.application.port.out.LoadAccountPort
import buckpal.account.application.port.out.UpdateAccountStatePort
import buckpal.account.domain.Account
import buckpal.account.domain.Account.AccountId
import buckpal.account.domain.BaselineDate
import buckpal.account.domain.Money

class SendMoneyServiceTest : BehaviorSpec({

    // Hand-rolled test double for LoadAccountPort. We cannot use `mockk` here
    // because the port now takes a `@JvmInline value class BaselineDate` and
    // mockk 1.13.8 cannot bind a matcher slot to a value-class parameter at
    // signature-detection time (JVM ABI unboxes to LocalDateTime while the
    // matcher is typed BaselineDate). All other collaborators stay mockk —
    // only loadAccount is affected because it is the sole port method that
    // exposes the new VO to its tests. Once mockk gains first-class inline-
    // value-class matchers, this can collapse back to a single mockk-based
    // setup using any<BaselineDate>().
    class StubbedLoadAccountPort : LoadAccountPort {
        private val accounts = mutableMapOf<AccountId, Account>()
        val loadCallsByAccountId: MutableMap<AccountId, Int> = mutableMapOf()
        val loadCallsBaseline: MutableList<BaselineDate> = mutableListOf()

        fun put(id: AccountId, account: Account) {
            accounts[id] = account
        }

        override fun loadAccount(accountId: AccountId, baselineDate: BaselineDate): Account {
            loadCallsByAccountId[accountId] = (loadCallsByAccountId[accountId] ?: 0) + 1
            loadCallsBaseline.add(baselineDate)
            return accounts[accountId] ?: error("no stub for $accountId")
        }
    }

    // Each leaf re-runs the spec lambda, so every leaf gets a fresh mock
    // graph — no shared mutable state, no clearMocks() gymnastics needed.
    // `relaxUnitFun = true` is applied to the two collaborators whose mocked
    // methods all return `Unit` (lockAccount / releaseAccount /
    // updateActivities) so we never have to stub a no-op return; everything
    // else stays strict.
    fun moneyTransferProperties(): MoneyTransferProperties =
        MoneyTransferProperties(Money.of(Long.MAX_VALUE))

    fun givenAnAccountWithId(
        loadAccountPort: StubbedLoadAccountPort,
        id: AccountId,
    ): Account {
        val account = mockk<Account>()
        every { account.id } returns id
        loadAccountPort.put(id, account)
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
                val loadAccountPort = StubbedLoadAccountPort()
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
                val loadAccountPort = StubbedLoadAccountPort()
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
                val loadAccountPort = StubbedLoadAccountPort()
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

                loadAccountPort.loadCallsByAccountId.size shouldBe 0
                verify(exactly = 0) { accountLock.lockAccount(any()) }
                verify(exactly = 0) { accountLock.releaseAccount(any()) }
                verify(exactly = 0) { updateAccountStatePort.updateActivities(any()) }
            }
        }
    }

    given("a withdrawal that succeeds but a deposit that fails") {
        `when`("sending money") {
            then("the result is false, both locks are acquired and released, and no activities are persisted") {
                val loadAccountPort = StubbedLoadAccountPort()
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
