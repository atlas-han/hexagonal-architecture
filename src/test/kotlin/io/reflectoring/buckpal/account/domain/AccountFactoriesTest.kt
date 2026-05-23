package io.reflectoring.buckpal.account.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.reflectoring.buckpal.account.domain.Account.AccountId

class AccountFactoriesTest : BehaviorSpec({

    given("Account.withoutId") {
        val baseline = Money.of(1_000L)
        val window = ActivityWindow()

        `when`("creating an Account without an id") {
            val account = Account.withoutId(baseline, window)

            then("the id is null") {
                account.id.shouldBeNull()
            }
            then("baselineBalance and activityWindow are wired through") {
                account.baselineBalance shouldBe baseline
                account.activityWindow shouldBe window
            }
            then("calculateBalance returns the baseline when no activities exist") {
                account.calculateBalance() shouldBe baseline
            }
        }
    }

    given("Account.withId") {
        val id = AccountId(7L)
        val baseline = Money.of(500L)
        val window = ActivityWindow()

        `when`("creating an Account with an id") {
            val account = Account.withId(id, baseline, window)

            then("the id is set") {
                account.id shouldBe id
            }
        }
    }

    given("the AccountId data class") {
        val a = AccountId(1L)
        val b = AccountId(1L)
        val c = AccountId(2L)

        `when`("comparing equal AccountIds") {
            then("they are equal and have the same hashCode") {
                a shouldBe b
                a.hashCode() shouldBe b.hashCode()
            }
        }

        `when`("comparing different AccountIds") {
            then("they are not equal") {
                a shouldNotBe c
            }
        }

        `when`("calling toString") {
            then("includes the value") {
                a.toString() shouldBe "AccountId(value=1)"
            }
        }
    }
})
