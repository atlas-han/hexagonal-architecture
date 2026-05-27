package io.reflectoring.buckpal.account.domain

/**
 * An account that holds a certain amount of money. An [Account] object only
 * contains a window of the latest account activities. The total balance of the
 * account is the sum of a baseline balance that was valid before the first
 * activity in the window and the sum of the activity values.
 */
open class Account private constructor(
    // open so SendMoneyServiceTest's Mockito mock can stub `account.id`;
    // the Kotlin primary-ctor `val` would otherwise compile to a final getter.
    open val id: AccountId?,
    val baselineBalance: Money,
    val activityWindow: ActivityWindow,
) {

    /**
     * Calculates the total balance of the account by adding the activity values
     * to the baseline balance.
     */
    open fun calculateBalance(): Money =
        Money.add(baselineBalance, activityWindow.calculateBalance(id))

    /**
     * Tries to withdraw a certain amount of money from this account.
     * If successful, creates a new activity with a negative value.
     * @return true if the withdrawal was successful, false if not.
     */
    open fun withdraw(money: Money, targetAccountId: AccountId): Boolean {
        if (!mayWithdraw(money)) {
            return false
        }
        // id is guaranteed non-null on persisted accounts; withdraw/deposit
        // are never called on an Account built via Account.withoutId.
        val ownerId = id!!
        val withdrawal = Activity(
            ownerId,
            ownerId,
            targetAccountId,
            ActivityTimestamp.now(),
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
        val ownerId = id!!
        val deposit = Activity(
            ownerId,
            sourceAccountId,
            ownerId,
            ActivityTimestamp.now(),
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
