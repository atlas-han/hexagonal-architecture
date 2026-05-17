package io.reflectoring.buckpal.account.domain

import java.time.LocalDateTime
import java.util.Optional

/**
 * An account that holds a certain amount of money. An [Account] object only
 * contains a window of the latest account activities. The total balance of the
 * account is the sum of a baseline balance that was valid before the first
 * activity in the window and the sum of the activity values.
 */
open class Account private constructor(
    private val _id: AccountId?,
    val baselineBalance: Money,
    val activityWindow: ActivityWindow,
) {

    /**
     * Kotlin-friendly nullable access to the account id. Routes through
     * [getId] so that tests mocking `account.getId()` (Optional form,
     * Sprint 4 + Sprint 8 transitional concern) still drive the property
     * read. Once Sprint 8 converts the tests, this routing can collapse
     * back to a plain primary-ctor `val id: AccountId?`.
     */
    @get:JvmName("getIdOrNull")
    val id: AccountId? get() = getId().orElse(null)

    /**
     * Returns the account's id wrapped in [Optional], for Java callers that
     * have not yet been migrated. Kotlin callers should prefer the [id]
     * property directly.
     */
    // open for Mockito mocking; the original Java class was non-final.
    open fun getId(): Optional<AccountId> = Optional.ofNullable(_id)

    /**
     * Calculates the total balance of the account by adding the activity values
     * to the baseline balance.
     */
    open fun calculateBalance(): Money =
        Money.add(baselineBalance, activityWindow.calculateBalance(_id))

    /**
     * Tries to withdraw a certain amount of money from this account.
     * If successful, creates a new activity with a negative value.
     * @return true if the withdrawal was successful, false if not.
     */
    open fun withdraw(money: Money, targetAccountId: AccountId): Boolean {
        if (!mayWithdraw(money)) {
            return false
        }
        // _id is guaranteed non-null on persisted accounts; withdraw is never
        // called on an unpersisted Account (Account.withoutId is only used in
        // the persistence-adapter factory path before save).
        val ownerId = _id!!
        val withdrawal = Activity(
            ownerId,
            ownerId,
            targetAccountId,
            LocalDateTime.now(),
            money,
        )
        activityWindow.addActivity(withdrawal)
        return true
    }

    private fun mayWithdraw(money: Money): Boolean =
        Money.add(calculateBalance(), money.negate()).isPositiveOrZero()

    /**
     * Tries to deposit a certain amount of money to this account.
     * If successful, creates a new activity with a positive value.
     * @return true if the deposit was successful, false if not.
     */
    open fun deposit(money: Money, sourceAccountId: AccountId): Boolean {
        // _id is guaranteed non-null on persisted accounts; deposit is never
        // called on an unpersisted Account.
        val ownerId = _id!!
        val deposit = Activity(
            ownerId,
            sourceAccountId,
            ownerId,
            LocalDateTime.now(),
            money,
        )
        activityWindow.addActivity(deposit)
        return true
    }

    companion object {
        /**
         * Creates an [Account] entity without an ID. Use to create a new entity
         * that is not yet persisted.
         */
        @JvmStatic
        fun withoutId(baselineBalance: Money, activityWindow: ActivityWindow): Account =
            Account(null, baselineBalance, activityWindow)

        /**
         * Creates an [Account] entity with an ID. Use to reconstitute a
         * persisted entity.
         */
        @JvmStatic
        fun withId(
            accountId: AccountId,
            baselineBalance: Money,
            activityWindow: ActivityWindow,
        ): Account = Account(accountId, baselineBalance, activityWindow)
    }

    data class AccountId(val value: Long)
}
