package io.reflectoring.buckpal.common

import io.reflectoring.buckpal.account.domain.Account.AccountId
import io.reflectoring.buckpal.account.domain.Activity
import io.reflectoring.buckpal.account.domain.Activity.ActivityId
import io.reflectoring.buckpal.account.domain.Money
import java.time.LocalDateTime

object ActivityTestData {

    @JvmStatic
    fun defaultActivity(): ActivityBuilder = ActivityBuilder(
        id = null,
        ownerAccountId = AccountId(42L),
        sourceAccountId = AccountId(42L),
        targetAccountId = AccountId(41L),
        timestamp = LocalDateTime.now(),
        money = Money.of(999L),
    )

    class ActivityBuilder internal constructor(
        private var id: ActivityId?,
        private var ownerAccountId: AccountId,
        private var sourceAccountId: AccountId,
        private var targetAccountId: AccountId,
        private var timestamp: LocalDateTime,
        private var money: Money,
    ) {

        fun withId(id: ActivityId?): ActivityBuilder = apply { this.id = id }

        fun withOwnerAccount(accountId: AccountId): ActivityBuilder = apply { this.ownerAccountId = accountId }

        fun withSourceAccount(accountId: AccountId): ActivityBuilder = apply { this.sourceAccountId = accountId }

        fun withTargetAccount(accountId: AccountId): ActivityBuilder = apply { this.targetAccountId = accountId }

        fun withTimestamp(timestamp: LocalDateTime): ActivityBuilder = apply { this.timestamp = timestamp }

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
