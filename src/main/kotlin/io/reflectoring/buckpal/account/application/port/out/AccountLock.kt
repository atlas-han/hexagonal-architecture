package io.reflectoring.buckpal.account.application.port.out

import io.reflectoring.buckpal.account.domain.Account

interface AccountLock {

    fun lockAccount(accountId: Account.AccountId)

    fun releaseAccount(accountId: Account.AccountId)
}
