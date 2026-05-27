package buckpal.account.application.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import buckpal.account.domain.Money

class ThresholdExceededExceptionTest : BehaviorSpec({

    given("a threshold and an actual transfer amount that exceeds it") {
        val threshold = Money.of(100L)
        val actual = Money.of(500L)
        val exception = ThresholdExceededException(threshold, actual)

        `when`("inspecting the exception type") {
            then("it is a RuntimeException") {
                exception.shouldBeInstanceOf<RuntimeException>()
            }
        }

        `when`("inspecting the exception message") {
            then("the message contains both threshold and actual amounts") {
                val message = exception.message
                message shouldBe
                    "Maximum threshold for transferring money exceeded: " +
                    "tried to transfer $actual but threshold is $threshold!"
                message!! shouldContain threshold.toString()
                message shouldContain actual.toString()
            }
        }
    }
})
