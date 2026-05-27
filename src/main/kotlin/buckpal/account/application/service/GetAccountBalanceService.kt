package io.reflectoring.buckpal.account.application.service

import io.reflectoring.buckpal.account.application.port.`in`.GetAccountBalanceQuery
import io.reflectoring.buckpal.account.application.port.out.LoadAccountPort
import io.reflectoring.buckpal.account.domain.Account
import io.reflectoring.buckpal.account.domain.BaselineDate
import io.reflectoring.buckpal.account.domain.Money

internal class GetAccountBalanceService(
    private val loadAccountPort: LoadAccountPort,
) : GetAccountBalanceQuery {

    override fun getAccountBalance(accountId: Account.AccountId): Money =
        loadAccountPort.loadAccount(accountId, BaselineDate.now()).calculateBalance()
}
