package io.reflectoring.buckpal.account.adapter.out.persistence

import io.reflectoring.buckpal.account.application.port.out.LoadAccountPort
import io.reflectoring.buckpal.account.application.port.out.UpdateAccountStatePort
import io.reflectoring.buckpal.account.domain.Account
import io.reflectoring.buckpal.common.PersistenceAdapter
import java.time.LocalDateTime
import javax.persistence.EntityNotFoundException

@PersistenceAdapter
internal class AccountPersistenceAdapter(
    private val accountRepository: SpringDataAccountRepository,
    private val activityRepository: ActivityRepository,
    private val accountMapper: AccountMapper,
) : LoadAccountPort, UpdateAccountStatePort {

    override fun loadAccount(
        accountId: Account.AccountId,
        baselineDate: LocalDateTime,
    ): Account {
        val account = accountRepository.findById(accountId.value)
            .orElseThrow { EntityNotFoundException() }

        val activities = activityRepository.findByOwnerSince(
            accountId.value,
            baselineDate,
        )

        val withdrawalBalance = activityRepository.getWithdrawalBalanceUntil(
            accountId.value,
            baselineDate,
        ) ?: 0L

        val depositBalance = activityRepository.getDepositBalanceUntil(
            accountId.value,
            baselineDate,
        ) ?: 0L

        return accountMapper.mapToDomainEntity(
            account,
            activities,
            withdrawalBalance,
            depositBalance,
        )
    }

    override fun updateActivities(account: Account) {
        for (activity in account.activityWindow.getActivities()) {
            if (activity.id == null) {
                activityRepository.save(accountMapper.mapToJpaEntity(activity))
            }
        }
    }
}
