package io.reflectoring.buckpal.account.adapter.`in`.web

import io.reflectoring.buckpal.account.application.port.`in`.SendMoneyCommand
import io.reflectoring.buckpal.account.application.port.`in`.SendMoneyUseCase
import io.reflectoring.buckpal.account.domain.Account
import io.reflectoring.buckpal.account.domain.Money
import io.reflectoring.buckpal.common.WebAdapter
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
