package io.reflectoring.buckpal.account.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigInteger

class MoneyTest : BehaviorSpec({

    given("a positive Money of 10") {
        val ten = Money.of(10L)

        `when`("checking sign predicates") {
            then("isPositiveOrZero is true") {
                ten.isPositiveOrZero().shouldBeTrue()
            }
            then("isPositive is true") {
                ten.isPositive().shouldBeTrue()
            }
            then("isNegative is false") {
                ten.isNegative().shouldBeFalse()
            }
        }
    }

    given("a zero Money") {
        val zero = Money.ZERO

        `when`("checking sign predicates") {
            then("isPositiveOrZero is true") {
                zero.isPositiveOrZero().shouldBeTrue()
            }
            then("isPositive is false") {
                zero.isPositive().shouldBeFalse()
            }
            then("isNegative is false") {
                zero.isNegative().shouldBeFalse()
            }
            then("amount equals BigInteger.ZERO") {
                zero.amount shouldBe BigInteger.ZERO
            }
        }
    }

    given("a negative Money of -7") {
        val negativeSeven = Money.of(-7L)

        `when`("checking sign predicates") {
            then("isPositiveOrZero is false") {
                negativeSeven.isPositiveOrZero().shouldBeFalse()
            }
            then("isPositive is false") {
                negativeSeven.isPositive().shouldBeFalse()
            }
            then("isNegative is true") {
                negativeSeven.isNegative().shouldBeTrue()
            }
        }
    }

    given("two Money values for comparison: 10 and 5") {
        val ten = Money.of(10L)
        val five = Money.of(5L)
        val anotherTen = Money.of(10L)

        `when`("calling isGreaterThanOrEqualTo") {
            then("10 >= 5 is true") {
                ten.isGreaterThanOrEqualTo(five).shouldBeTrue()
            }
            then("10 >= 10 is true (equal case)") {
                ten.isGreaterThanOrEqualTo(anotherTen).shouldBeTrue()
            }
            then("5 >= 10 is false") {
                five.isGreaterThanOrEqualTo(ten).shouldBeFalse()
            }
        }

        `when`("calling isGreaterThan") {
            then("10 > 5 is true") {
                ten.isGreaterThan(five).shouldBeTrue()
            }
            then("10 > 10 is false (equal case)") {
                ten.isGreaterThan(anotherTen).shouldBeFalse()
            }
            then("5 > 10 is false") {
                five.isGreaterThan(ten).shouldBeFalse()
            }
        }
    }

    given("two Money values: 10 and 3") {
        val ten = Money.of(10L)
        val three = Money.of(3L)

        `when`("using the plus operator") {
            then("10 + 3 equals 13") {
                (ten + three) shouldBe Money.of(13L)
            }
        }

        `when`("using the minus operator") {
            then("10 - 3 equals 7") {
                (ten - three) shouldBe Money.of(7L)
            }
        }

        `when`("calling negate") {
            then("negate(10) equals -10") {
                ten.negate() shouldBe Money.of(-10L)
            }
            then("negate(-10) equals 10") {
                Money.of(-10L).negate() shouldBe ten
            }
        }
    }

    given("the Money companion factories") {
        `when`("calling Money.of with a Long") {
            then("constructs a Money with the matching BigInteger amount") {
                Money.of(42L).amount shouldBe BigInteger.valueOf(42L)
            }
        }

        `when`("calling Money.add") {
            then("returns the sum of the two values") {
                Money.add(Money.of(4L), Money.of(6L)) shouldBe Money.of(10L)
            }
        }

        `when`("calling Money.subtract") {
            then("returns the difference of the two values") {
                Money.subtract(Money.of(10L), Money.of(3L)) shouldBe Money.of(7L)
            }
        }

        `when`("reading Money.ZERO") {
            then("equals Money(BigInteger.ZERO)") {
                Money.ZERO shouldBe Money(BigInteger.ZERO)
            }
        }
    }

    given("the Money data class contract") {
        val a = Money.of(123L)
        val b = Money.of(123L)
        val c = Money.of(124L)

        `when`("comparing equal instances") {
            then("they are equal and have the same hashCode") {
                a shouldBe b
                a.hashCode() shouldBe b.hashCode()
            }
        }

        `when`("comparing different instances") {
            then("they are not equal") {
                a shouldNotBe c
            }
        }

        `when`("calling copy") {
            then("produces an equal instance when arguments are unchanged") {
                a.copy() shouldBe a
            }
            then("produces a different instance when amount is changed") {
                a.copy(amount = BigInteger.valueOf(999L)) shouldBe Money.of(999L)
            }
        }

        `when`("calling toString") {
            then("includes the amount") {
                a.toString() shouldBe "Money(amount=123)"
            }
        }
    }
})
