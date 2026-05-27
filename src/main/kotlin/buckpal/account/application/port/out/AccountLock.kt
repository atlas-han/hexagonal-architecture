package buckpal.account.application.port.out

import buckpal.account.domain.Account

interface AccountLock {

    fun lockAccount(accountId: Account.AccountId)

    fun releaseAccount(accountId: Account.AccountId)
}
