package io.reflectoring.buckpal.common

import io.reflectoring.buckpal.account.domain.Account
import io.reflectoring.buckpal.account.domain.Account.AccountId
import io.reflectoring.buckpal.account.domain.ActivityWindow
import io.reflectoring.buckpal.account.domain.Money

object AccountTestData {

    @JvmStatic
    fun defaultAccount(): AccountBuilder = AccountBuilder(
        accountId = AccountId(42L),
        baselineBalance = Money.of(999L),
        activityWindow = ActivityWindow(
            ActivityTestData.defaultActivity().build(),
            ActivityTestData.defaultActivity().build(),
        ),
    )

    class AccountBuilder internal constructor(
        private var accountId: AccountId,
        private var baselineBalance: Money,
        private var activityWindow: ActivityWindow,
    ) {

        fun withAccountId(accountId: AccountId): AccountBuilder = apply { this.accountId = accountId }

        fun withBaselineBalance(baselineBalance: Money): AccountBuilder = apply { this.baselineBalance = baselineBalance }

        fun withActivityWindow(activityWindow: ActivityWindow): AccountBuilder = apply { this.activityWindow = activityWindow }

        fun build(): Account = Account.withId(accountId, baselineBalance, activityWindow)
    }
}
