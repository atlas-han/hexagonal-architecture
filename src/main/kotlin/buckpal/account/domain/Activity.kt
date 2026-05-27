package io.reflectoring.buckpal.account.domain

/**
 * A money transfer activity between [Account]s.
 */
data class Activity(
    val id: ActivityId?,
    val ownerAccountId: Account.AccountId,
    val sourceAccountId: Account.AccountId,
    val targetAccountId: Account.AccountId,
    val timestamp: ActivityTimestamp,
    val money: Money,
) {

    constructor(
        ownerAccountId: Account.AccountId,
        sourceAccountId: Account.AccountId,
        targetAccountId: Account.AccountId,
        timestamp: ActivityTimestamp,
        money: Money,
    ) : this(null, ownerAccountId, sourceAccountId, targetAccountId, timestamp, money)

    data class ActivityId(val value: Long)
}
