package io.reflectoring.buckpal.account.domain

import java.time.LocalDateTime

/**
 * A money transfer activity between [Account]s.
 */
data class Activity(
    val id: ActivityId?,
    val ownerAccountId: Account.AccountId,
    val sourceAccountId: Account.AccountId,
    val targetAccountId: Account.AccountId,
    val timestamp: LocalDateTime,
    val money: Money,
) {

    constructor(
        ownerAccountId: Account.AccountId,
        sourceAccountId: Account.AccountId,
        targetAccountId: Account.AccountId,
        timestamp: LocalDateTime,
        money: Money,
    ) : this(null, ownerAccountId, sourceAccountId, targetAccountId, timestamp, money)

    data class ActivityId(val value: Long)
}
