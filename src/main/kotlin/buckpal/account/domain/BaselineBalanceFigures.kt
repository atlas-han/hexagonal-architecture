package io.reflectoring.buckpal.account.domain

data class BaselineBalanceFigures(
    val deposit: Money,
    val withdrawal: Money,
) {

    fun toBaselineBalance(): Money = deposit - withdrawal
}
