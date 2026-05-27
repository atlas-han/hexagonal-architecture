package buckpal

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BuckPalApplicationTests : DescribeSpec() {

    override fun extensions() = listOf(SpringExtension)

    init {
        describe("Spring application context") {
            it("loads") {
                // intentionally empty: success is the absence of
                // BeanCreationException from the @SpringBootTest container
            }
        }
    }
}
