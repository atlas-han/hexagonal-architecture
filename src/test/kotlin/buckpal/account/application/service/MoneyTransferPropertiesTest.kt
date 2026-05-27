package io.reflectoring.buckpal.account.application.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.reflectoring.buckpal.account.domain.Money

class MoneyTransferPropertiesTest : BehaviorSpec({

    given("the default constructor") {
        `when`("instantiated with no arguments") {
            then("maximumTransferThreshold defaults to 1,000,000") {
                val properties = MoneyTransferProperties()
                properties.maximumTransferThreshold shouldBe Money.of(1_000_000L)
            }
        }
    }

    given("the custom constructor") {
        `when`("instantiated with a custom threshold") {
            then("maximumTransferThreshold reflects the provided value") {
                val custom = Money.of(2_500L)
                val properties = MoneyTransferProperties(custom)
                properties.maximumTransferThreshold shouldBe custom
            }
        }
    }

    given("an existing MoneyTransferProperties instance") {
        `when`("the maximumTransferThreshold var is reassigned") {
            then("the property reflects the new value") {
                val properties = MoneyTransferProperties()
                val newThreshold = Money.of(42L)
                properties.maximumTransferThreshold = newThreshold
                properties.maximumTransferThreshold shouldBe newThreshold
            }
        }
    }

    given("two MoneyTransferProperties instances") {
        `when`("they hold the same maximumTransferThreshold") {
            then("they are equal as data classes") {
                val a = MoneyTransferProperties(Money.of(777L))
                val b = MoneyTransferProperties(Money.of(777L))
                a shouldBe b
                a.hashCode() shouldBe b.hashCode()
            }
        }

        `when`("they hold different thresholds") {
            then("they are not equal") {
                val a = MoneyTransferProperties(Money.of(100L))
                val b = MoneyTransferProperties(Money.of(200L))
                a shouldNotBe b
            }
        }
    }
})
