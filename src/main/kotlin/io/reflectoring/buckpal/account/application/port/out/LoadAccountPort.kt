package io.reflectoring.buckpal.account.application.port.out

import io.reflectoring.buckpal.account.domain.Account
import java.time.LocalDateTime

interface LoadAccountPort {

    fun loadAccount(accountId: Account.AccountId, baselineDate: LocalDateTime): Account
}
