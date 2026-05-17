package io.reflectoring.buckpal.account.application.service

import io.reflectoring.buckpal.account.application.port.`in`.SendMoneyCommand
import io.reflectoring.buckpal.account.application.port.out.AccountLock
import io.reflectoring.buckpal.account.application.port.out.LoadAccountPort
import io.reflectoring.buckpal.account.application.port.out.UpdateAccountStatePort
import io.reflectoring.buckpal.account.domain.Account
import io.reflectoring.buckpal.account.domain.Account.AccountId
import io.reflectoring.buckpal.account.domain.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mockito
import org.mockito.Mockito.times
import java.time.LocalDateTime
import java.util.Optional
import java.util.stream.Collectors

class SendMoneyServiceTest {

    private val loadAccountPort: LoadAccountPort =
        Mockito.mock(LoadAccountPort::class.java)

    private val accountLock: AccountLock =
        Mockito.mock(AccountLock::class.java)

    private val updateAccountStatePort: UpdateAccountStatePort =
        Mockito.mock(UpdateAccountStatePort::class.java)

    private val sendMoneyService: SendMoneyService =
        SendMoneyService(loadAccountPort, accountLock, updateAccountStatePort, moneyTransferProperties())

    // Non-null sentinel passed to verified mock calls when a Mockito matcher
    // is already queued. Mockito's matcher queue is thread-local; once
    // `ArgumentCaptor.capture()` (or `eq` / `any`) has reported a matcher,
    // the proxy invocation uses that matcher and ignores the actual argument
    // value. Supplying this sentinel lets us satisfy Kotlin's strict
    // null-safety on non-null `Account` parameters without introducing
    // `mockito-kotlin` (out of sprint scope).
    private val accountSentinel: Account = Mockito.mock(Account::class.java)

    @Test
    fun givenWithdrawalFails_thenOnlySourceAccountIsLockedAndReleased() {
        val sourceAccountId = AccountId(41L)
        val sourceAccount = givenAnAccountWithId(sourceAccountId)

        val targetAccountId = AccountId(42L)
        val targetAccount = givenAnAccountWithId(targetAccountId)

        givenWithdrawalWillFail(sourceAccount)
        givenDepositWillSucceed(targetAccount)

        val command = SendMoneyCommand(
            sourceAccountId,
            targetAccountId,
            Money.of(300L),
        )

        val success = sendMoneyService.sendMoney(command)

        assertThat(success).isFalse()

        then(accountLock).should().lockAccount(eq(sourceAccountId))
        then(accountLock).should().releaseAccount(eq(sourceAccountId))
        then(accountLock).should(times(0)).lockAccount(eq(targetAccountId))
    }

    @Test
    fun transactionSucceeds() {
        val sourceAccount = givenSourceAccount()
        val targetAccount = givenTargetAccount()

        givenWithdrawalWillSucceed(sourceAccount)
        givenDepositWillSucceed(targetAccount)

        val money = Money.of(500L)

        val command = SendMoneyCommand(
            sourceAccount.getId().get(),
            targetAccount.getId().get(),
            money,
        )

        val success = sendMoneyService.sendMoney(command)

        assertThat(success).isTrue()

        val sourceAccountId = sourceAccount.getId().get()
        val targetAccountId = targetAccount.getId().get()

        then(accountLock).should().lockAccount(eq(sourceAccountId))
        then(sourceAccount).should().withdraw(eq(money), eq(targetAccountId))
        then(accountLock).should().releaseAccount(eq(sourceAccountId))

        then(accountLock).should().lockAccount(eq(targetAccountId))
        then(targetAccount).should().deposit(eq(money), eq(sourceAccountId))
        then(accountLock).should().releaseAccount(eq(targetAccountId))

        thenAccountsHaveBeenUpdated(sourceAccountId, targetAccountId)
    }

    private fun thenAccountsHaveBeenUpdated(vararg accountIds: AccountId) {
        val accountCaptor = ArgumentCaptor.forClass(Account::class.java)
        then(updateAccountStatePort).should(times(accountIds.size))
            .updateActivities(capture(accountCaptor))

        val updatedAccountIds: List<AccountId> = accountCaptor.allValues
            .stream()
            .map(Account::getId)
            .map(Optional<AccountId>::get)
            .collect(Collectors.toList())

        for (accountId in accountIds) {
            assertThat(updatedAccountIds).contains(accountId)
        }
    }

    // Mockito's ArgumentCaptor.capture() registers a matcher and returns
    // null; this wrapper preserves the matcher-registration side effect and
    // substitutes the non-null `accountSentinel` so Kotlin's strict
    // null-safety accepts the proxy call. Mockito uses the queued matcher
    // (not the sentinel) to record the real invocation argument.
    private fun capture(captor: ArgumentCaptor<Account>): Account {
        captor.capture()
        return accountSentinel
    }

    private fun givenDepositWillSucceed(account: Account) {
        // Pre-queue `any` matchers; pass non-null sentinel values to satisfy
        // Kotlin's strict null-safety on the non-null parameters of
        // `Account.deposit(Money, AccountId)`.
        Mockito.any(Money::class.java)
        Mockito.any(AccountId::class.java)
        given(account.deposit(Money.of(0L), AccountId(0L)))
            .willReturn(true)
    }

    private fun givenWithdrawalWillFail(account: Account) {
        Mockito.any(Money::class.java)
        Mockito.any(AccountId::class.java)
        given(account.withdraw(Money.of(0L), AccountId(0L)))
            .willReturn(false)
    }

    private fun givenWithdrawalWillSucceed(account: Account) {
        Mockito.any(Money::class.java)
        Mockito.any(AccountId::class.java)
        given(account.withdraw(Money.of(0L), AccountId(0L)))
            .willReturn(true)
    }

    private fun givenTargetAccount(): Account = givenAnAccountWithId(AccountId(42L))

    private fun givenSourceAccount(): Account = givenAnAccountWithId(AccountId(41L))

    private fun givenAnAccountWithId(id: AccountId): Account {
        val account = Mockito.mock(Account::class.java)
        given(account.getId())
            .willReturn(Optional.of(id))
        // Compute sentinel values BEFORE registering matchers, otherwise an
        // intervening `account.getId()` mock invocation would consume the
        // queued matchers and Mockito would report
        // "0 matchers expected, 2 recorded".
        val accountIdValue = account.getId().get()
        val now = LocalDateTime.now()
        Mockito.eq(accountIdValue)
        Mockito.any(LocalDateTime::class.java)
        given(loadAccountPort.loadAccount(accountIdValue, now))
            .willReturn(account)
        return account
    }

    private fun moneyTransferProperties(): MoneyTransferProperties =
        MoneyTransferProperties(Money.of(Long.MAX_VALUE))

    // Mockito.eq returns `null` at runtime to register the matcher; the
    // `?: value` fallback keeps Kotlin's non-null parameter contract intact.
    // The unbound `<T>` (no `: Any`) avoids the Kotlin compiler inserting an
    // additional intrinsic null-check on the return value.
    private fun <T> eq(value: T): T = Mockito.eq(value) ?: value
}
