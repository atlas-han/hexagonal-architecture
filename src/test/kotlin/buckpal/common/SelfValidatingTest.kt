package buckpal.common

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import jakarta.validation.ConstraintViolationException
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

class SelfValidatingTest : DescribeSpec({

    // A small subclass that exercises both the success and failure paths
    // of `SelfValidating.validateSelf()`. It deliberately does NOT call
    // `validateSelf()` from its constructor so each test can drive
    // validation explicitly.
    class Sample(
        @field:NotNull val name: String?,
        @field:Min(1) val count: Int,
    ) : SelfValidating<Sample>() {
        fun validate() = validateSelf()
    }

    describe("SelfValidating.validateSelf()") {

        it("does not throw when all bean-validation constraints are satisfied") {
            val sample = Sample(name = "valid", count = 5)

            shouldNotThrowAny { sample.validate() }
        }

        it("throws ConstraintViolationException when a constraint is violated") {
            val sample = Sample(name = null, count = 0)

            val ex = shouldThrow<ConstraintViolationException> {
                sample.validate()
            }
            ex.constraintViolations shouldNotBe null
            (ex.constraintViolations.size >= 1) shouldNotBe false
        }
    }
})
