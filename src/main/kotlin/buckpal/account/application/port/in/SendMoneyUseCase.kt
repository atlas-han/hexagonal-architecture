package io.reflectoring.buckpal.account.application.port.`in`

interface SendMoneyUseCase {

    fun sendMoney(command: SendMoneyCommand): Boolean
}
