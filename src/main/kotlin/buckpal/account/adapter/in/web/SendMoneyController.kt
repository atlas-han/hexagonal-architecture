package buckpal.account.adapter.`in`.web

import buckpal.account.application.port.`in`.SendMoneyCommand
import buckpal.account.application.port.`in`.SendMoneyUseCase
import buckpal.account.domain.Account
import buckpal.account.domain.Money
import buckpal.common.WebAdapter
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@WebAdapter
@RestController
internal class SendMoneyController(
    private val sendMoneyUseCase: SendMoneyUseCase,
) {

    @PostMapping("/accounts/send/{sourceAccountId}/{targetAccountId}/{amount}")
    fun sendMoney(
        @PathVariable("sourceAccountId") sourceAccountId: Long,
        @PathVariable("targetAccountId") targetAccountId: Long,
        @PathVariable("amount") amount: Long,
    ) {
        val command = SendMoneyCommand(
            Account.AccountId(sourceAccountId),
            Account.AccountId(targetAccountId),
            Money.of(amount),
        )
        sendMoneyUseCase.sendMoney(command)
    }
}
