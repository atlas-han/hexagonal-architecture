package io.reflectoring.buckpal.account.adapter.out.persistence

import io.reflectoring.buckpal.account.domain.Account
import io.reflectoring.buckpal.account.domain.Activity
import io.reflectoring.buckpal.account.domain.ActivityTimestamp
import io.reflectoring.buckpal.account.domain.ActivityWindow
import io.reflectoring.buckpal.account.domain.BaselineBalanceFigures
import io.reflectoring.buckpal.account.domain.Money
import org.springframework.stereotype.Component

@Component
internal class AccountMapper {

    fun mapToDomainEntity(
        account: AccountJpaEntity,
        activities: List<ActivityJpaEntity>,
        figures: BaselineBalanceFigures,
    ): Account {
        val baselineBalance = figures.toBaselineBalance()

        val accountId = requireNotNull(account.id) {
            "AccountJpaEntity loaded without id"
        }
        return Account.withId(
            Account.AccountId(accountId),
            baselineBalance,
            mapToActivityWindow(activities),
        )
    }

    fun mapToActivityWindow(activities: List<ActivityJpaEntity>): ActivityWindow {
        val mappedActivities = activities.map { activity ->
            val id = requireNotNull(activity.id) {
                "ActivityJpaEntity loaded without id"
            }
            val ownerAccountId = requireNotNull(activity.ownerAccountId) {
                "ActivityJpaEntity loaded without ownerAccountId"
            }
            val sourceAccountId = requireNotNull(activity.sourceAccountId) {
                "ActivityJpaEntity loaded without sourceAccountId"
            }
            val targetAccountId = requireNotNull(activity.targetAccountId) {
                "ActivityJpaEntity loaded without targetAccountId"
            }
            val timestamp = requireNotNull(activity.timestamp) {
                "ActivityJpaEntity loaded without timestamp"
            }
            val amount = requireNotNull(activity.amount) {
                "ActivityJpaEntity loaded without amount"
            }
            Activity(
                Activity.ActivityId(id),
                Account.AccountId(ownerAccountId),
                Account.AccountId(sourceAccountId),
                Account.AccountId(targetAccountId),
                ActivityTimestamp(timestamp),
                Money.of(amount),
            )
        }
        return ActivityWindow(mappedActivities.toMutableList())
    }

    fun mapToJpaEntity(activity: Activity): ActivityJpaEntity =
        ActivityJpaEntity(
            id = activity.id?.value,
            timestamp = activity.timestamp.value,
            ownerAccountId = activity.ownerAccountId.value,
            sourceAccountId = activity.sourceAccountId.value,
            targetAccountId = activity.targetAccountId.value,
            amount = activity.money.amount.toLong(),
        )
}
