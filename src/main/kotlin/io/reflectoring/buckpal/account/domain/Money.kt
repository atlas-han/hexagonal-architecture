package io.reflectoring.buckpal.account.domain

import java.math.BigInteger

data class Money(val amount: BigInteger) {

    fun isPositiveOrZero(): Boolean = amount.compareTo(BigInteger.ZERO) >= 0

    fun isNegative(): Boolean = amount.compareTo(BigInteger.ZERO) < 0

    fun isPositive(): Boolean = amount.compareTo(BigInteger.ZERO) > 0

    fun isGreaterThanOrEqualTo(money: Money): Boolean = amount.compareTo(money.amount) >= 0

    fun isGreaterThan(money: Money): Boolean = amount.compareTo(money.amount) >= 1

    operator fun plus(money: Money): Money = Money(amount.add(money.amount))

    operator fun minus(money: Money): Money = Money(amount.subtract(money.amount))

    fun negate(): Money = Money(amount.negate())

    companion object {
        @JvmField
        val ZERO: Money = Money(BigInteger.ZERO)

        @JvmStatic
        fun of(value: Long): Money = Money(BigInteger.valueOf(value))

        @JvmStatic
        fun add(a: Money, b: Money): Money = Money(a.amount.add(b.amount))

        @JvmStatic
        fun subtract(a: Money, b: Money): Money = Money(a.amount.subtract(b.amount))
    }
}
