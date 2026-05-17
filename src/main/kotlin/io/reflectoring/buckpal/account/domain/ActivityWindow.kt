package io.reflectoring.buckpal.account.domain

import java.time.LocalDateTime
import java.util.Collections

/**
 * A window of account activities.
 */
class ActivityWindow {

    private val activities: MutableList<Activity>

    constructor(activities: MutableList<Activity>) {
        this.activities = activities
    }

    constructor(vararg activities: Activity) {
        this.activities = activities.toMutableList()
    }

    /**
     * The timestamp of the first activity within this window.
     */
    fun getStartTimestamp(): LocalDateTime =
        activities.minByOrNull(Activity::timestamp)?.timestamp
            ?: throw IllegalStateException()

    /**
     * The timestamp of the last activity within this window.
     */
    fun getEndTimestamp(): LocalDateTime =
        activities.maxByOrNull(Activity::timestamp)?.timestamp
            ?: throw IllegalStateException()

    /**
     * Calculates the balance by summing up the values of all activities
     * within this window.
     */
    fun calculateBalance(accountId: Account.AccountId?): Money {
        val depositBalance = activities
            .filter { it.targetAccountId == accountId }
            .map(Activity::money)
            .fold(Money.ZERO, Money::add)

        val withdrawalBalance = activities
            .filter { it.sourceAccountId == accountId }
            .map(Activity::money)
            .fold(Money.ZERO, Money::add)

        return Money.add(depositBalance, withdrawalBalance.negate())
    }

    fun getActivities(): List<Activity> = Collections.unmodifiableList(activities)

    fun addActivity(activity: Activity) {
        activities.add(activity)
    }
}
