package buckpal.common

import buckpal.account.domain.Account.AccountId
import buckpal.account.domain.Activity
import buckpal.account.domain.Activity.ActivityId
import buckpal.account.domain.ActivityTimestamp
import buckpal.account.domain.Money
import java.time.LocalDateTime

object ActivityTestData {

    @JvmStatic
    fun defaultActivity(): ActivityBuilder = ActivityBuilder(
        id = null,
        ownerAccountId = AccountId(42L),
        sourceAccountId = AccountId(42L),
        targetAccountId = AccountId(41L),
        timestamp = ActivityTimestamp.now(),
        money = Money.of(999L),
    )

    class ActivityBuilder internal constructor(
        private var id: ActivityId?,
        private var ownerAccountId: AccountId,
        private var sourceAccountId: AccountId,
        private var targetAccountId: AccountId,
        private var timestamp: ActivityTimestamp,
        private var money: Money,
    ) {

        fun withId(id: ActivityId?): ActivityBuilder = apply { this.id = id }

        fun withOwnerAccount(accountId: AccountId): ActivityBuilder = apply { this.ownerAccountId = accountId }

        fun withSourceAccount(accountId: AccountId): ActivityBuilder = apply { this.sourceAccountId = accountId }

        fun withTargetAccount(accountId: AccountId): ActivityBuilder = apply { this.targetAccountId = accountId }

        fun withTimestamp(timestamp: ActivityTimestamp): ActivityBuilder = apply { this.timestamp = timestamp }

        fun withTimestamp(timestamp: LocalDateTime): ActivityBuilder = apply { this.timestamp = ActivityTimestamp(timestamp) }

        fun withMoney(money: Money): ActivityBuilder = apply { this.money = money }

        fun build(): Activity = Activity(
            id,
            ownerAccountId,
            sourceAccountId,
            targetAccountId,
            timestamp,
            money,
        )
    }
}
