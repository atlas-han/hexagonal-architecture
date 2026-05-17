package io.reflectoring.buckpal.account.application.service

import io.reflectoring.buckpal.account.domain.Money

/**
 * Configuration properties for money transfer use cases.
 */
data class MoneyTransferProperties @JvmOverloads constructor(
    var maximumTransferThreshold: Money = Money.of(1_000_000L),
)
