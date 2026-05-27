package io.reflectoring.buckpal.account.application.port.out

import io.reflectoring.buckpal.account.domain.Account
import io.reflectoring.buckpal.account.domain.BaselineDate

interface LoadAccountPort {

    fun loadAccount(accountId: Account.AccountId, baselineDate: BaselineDate): Account
}
