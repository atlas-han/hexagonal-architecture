package buckpal.account.application.port.out

import buckpal.account.domain.Account
import buckpal.account.domain.BaselineDate

interface LoadAccountPort {

    fun loadAccount(accountId: Account.AccountId, baselineDate: BaselineDate): Account
}
