package buckpal.account.adapter.out.persistence

import buckpal.account.application.port.out.LoadAccountPort
import buckpal.account.application.port.out.UpdateAccountStatePort
import buckpal.account.domain.Account
import buckpal.account.domain.BaselineBalanceFigures
import buckpal.account.domain.BaselineDate
import buckpal.account.domain.Money
import buckpal.common.PersistenceAdapter
import jakarta.persistence.EntityNotFoundException

@PersistenceAdapter
internal class AccountPersistenceAdapter(
    private val accountRepository: SpringDataAccountRepository,
    private val activityRepository: ActivityRepository,
    private val accountMapper: AccountMapper,
) : LoadAccountPort, UpdateAccountStatePort {

    override fun loadAccount(
        accountId: Account.AccountId,
        baselineDate: BaselineDate,
    ): Account {
        val account = accountRepository.findById(accountId.value)
            .orElseThrow { EntityNotFoundException() }

        val activities = activityRepository.findByOwnerSince(
            accountId.value,
            baselineDate.value,
        )

        val withdrawalBalance = activityRepository.getWithdrawalBalanceUntil(
            accountId.value,
            baselineDate.value,
        ) ?: 0L

        val depositBalance = activityRepository.getDepositBalanceUntil(
            accountId.value,
            baselineDate.value,
        ) ?: 0L

        val figures = BaselineBalanceFigures(
            deposit = Money.of(depositBalance),
            withdrawal = Money.of(withdrawalBalance),
        )

        return accountMapper.mapToDomainEntity(
            account,
            activities,
            figures,
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
