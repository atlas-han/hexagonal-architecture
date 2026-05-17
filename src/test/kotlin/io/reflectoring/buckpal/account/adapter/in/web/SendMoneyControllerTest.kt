package io.reflectoring.buckpal.account.adapter.`in`.web

import io.reflectoring.buckpal.account.application.port.`in`.SendMoneyCommand
import io.reflectoring.buckpal.account.application.port.`in`.SendMoneyUseCase
import io.reflectoring.buckpal.account.domain.Account.AccountId
import io.reflectoring.buckpal.account.domain.Money
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.then
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [SendMoneyController::class])
class SendMoneyControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var sendMoneyUseCase: SendMoneyUseCase

    @Test
    fun testSendMoney() {
        mockMvc.perform(
            post(
                "/accounts/send/{sourceAccountId}/{targetAccountId}/{amount}",
                41L, 42L, 500,
            )
                .header("Content-Type", "application/json"),
        )
            .andExpect(status().isOk())

        then(sendMoneyUseCase).should()
            .sendMoney(
                eq(
                    SendMoneyCommand(
                        AccountId(41L),
                        AccountId(42L),
                        Money.of(500L),
                    ),
                ),
            )
    }

    // Kotlin/Mockito null-safety bridge — Mockito.eq returns `null` at runtime
    // for matcher registration; the wrapper substitutes the captured value so
    // the call type-checks against the non-null Kotlin signature. See
    // SendMoneyServiceTest for the same idiom.
    private fun <T> eq(value: T): T = Mockito.eq(value) ?: value
}
