package buckpal.account.application.port.`in`

import buckpal.account.domain.Account
import buckpal.account.domain.Money
import buckpal.common.SelfValidating
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
