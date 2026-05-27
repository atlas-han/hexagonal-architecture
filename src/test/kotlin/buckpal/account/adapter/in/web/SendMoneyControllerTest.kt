package io.reflectoring.buckpal.account.adapter.`in`.web

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import io.mockk.verify
import io.reflectoring.buckpal.account.application.port.`in`.SendMoneyCommand
import io.reflectoring.buckpal.account.application.port.`in`.SendMoneyUseCase
import io.reflectoring.buckpal.account.domain.Account.AccountId
import io.reflectoring.buckpal.account.domain.Money
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [SendMoneyController::class])
class SendMoneyControllerTest : DescribeSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var sendMoneyUseCase: SendMoneyUseCase

    init {
        describe("POST /accounts/send/{sourceAccountId}/{targetAccountId}/{amount}") {
            it("returns 200 and forwards the SendMoneyCommand") {
                every { sendMoneyUseCase.sendMoney(any()) } returns true

                mockMvc.perform(
                    post(
                        "/accounts/send/{sourceAccountId}/{targetAccountId}/{amount}",
                        41L, 42L, 500,
                    )
                        .header("Content-Type", "application/json"),
                )
                    .andExpect(status().isOk)

                verify {
                    sendMoneyUseCase.sendMoney(
                        SendMoneyCommand(
                            AccountId(41L),
                            AccountId(42L),
                            Money.of(500L),
                        ),
                    )
                }
            }
        }
    }
}
