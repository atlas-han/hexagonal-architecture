package buckpal.account.application.service

import buckpal.account.domain.Money

/**
 * Configuration properties for money transfer use cases.
 */
data class MoneyTransferProperties @JvmOverloads constructor(
    var maximumTransferThreshold: Money = Money.of(1_000_000L),
)
