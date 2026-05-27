package io.reflectoring.buckpal.account.application.port.`in`

import io.reflectoring.buckpal.account.domain.Account
import io.reflectoring.buckpal.account.domain.Money
import io.reflectoring.buckpal.common.SelfValidating
import javax.validation.constraints.NotNull

data class SendMoneyCommand(
    @field:NotNull val sourceAccountId: Account.AccountId,
    @field:NotNull val targetAccountId: Account.AccountId,
    @field:NotNull val money: Money,
) : SelfValidating<SendMoneyCommand>() {

    init {
        validateSelf()
    }
}
