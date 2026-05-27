package buckpal.account.application.port.`in`

import buckpal.account.domain.Account
import buckpal.account.domain.Money

interface GetAccountBalanceQuery {

    fun getAccountBalance(accountId: Account.AccountId): Money
}
