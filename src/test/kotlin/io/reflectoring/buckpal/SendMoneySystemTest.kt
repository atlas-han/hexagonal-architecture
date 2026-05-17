package io.reflectoring.buckpal

import io.reflectoring.buckpal.account.application.port.out.LoadAccountPort
import io.reflectoring.buckpal.account.domain.Account
import io.reflectoring.buckpal.account.domain.Account.AccountId
import io.reflectoring.buckpal.account.domain.Money
import org.assertj.core.api.BDDAssertions.then
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SendMoneySystemTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var loadAccountPort: LoadAccountPort

    @Test
    @Sql("SendMoneySystemTest.sql")
    fun sendMoney() {
        val initialSourceBalance = sourceAccount().calculateBalance()
        val initialTargetBalance = targetAccount().calculateBalance()

        val response = whenSendMoney(
            sourceAccountId(),
            targetAccountId(),
            transferredAmount(),
        )

        then(response.statusCode)
            .isEqualTo(HttpStatus.OK)

        then(sourceAccount().calculateBalance())
            .isEqualTo(initialSourceBalance.minus(transferredAmount()))

        then(targetAccount().calculateBalance())
            .isEqualTo(initialTargetBalance.plus(transferredAmount()))
    }

    private fun sourceAccount(): Account = loadAccount(sourceAccountId())

    private fun targetAccount(): Account = loadAccount(targetAccountId())

    private fun loadAccount(accountId: AccountId): Account =
        loadAccountPort.loadAccount(accountId, LocalDateTime.now())

    private fun whenSendMoney(
        sourceAccountId: AccountId,
        targetAccountId: AccountId,
        amount: Money,
    ): ResponseEntity<*> {
        val headers = HttpHeaders()
        headers.add("Content-Type", "application/json")
        val request = HttpEntity<Void>(null, headers)

        return restTemplate.exchange(
            "/accounts/send/{sourceAccountId}/{targetAccountId}/{amount}",
            HttpMethod.POST,
            request,
            Any::class.java,
            sourceAccountId.value,
            targetAccountId.value,
            amount.amount,
        )
    }

    private fun transferredAmount(): Money = Money.of(500L)

    private fun sourceAccountId(): AccountId = AccountId(1L)

    private fun targetAccountId(): AccountId = AccountId(2L)
}
