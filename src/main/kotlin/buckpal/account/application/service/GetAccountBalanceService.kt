package buckpal.account.application.service

import buckpal.account.application.port.`in`.GetAccountBalanceQuery
import buckpal.account.application.port.out.LoadAccountPort
import buckpal.account.domain.Account
import buckpal.account.domain.BaselineDate
import buckpal.account.domain.Money

internal class GetAccountBalanceService(
    private val loadAccountPort: LoadAccountPort,
) : GetAccountBalanceQuery {

    override fun getAccountBalance(accountId: Account.AccountId): Money =
        loadAccountPort.loadAccount(accountId, BaselineDate.now()).calculateBalance()
}
