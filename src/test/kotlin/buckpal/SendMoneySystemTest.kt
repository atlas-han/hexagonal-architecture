package buckpal

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import buckpal.account.application.port.out.LoadAccountPort
import buckpal.account.domain.Account
import buckpal.account.domain.Account.AccountId
import buckpal.account.domain.BaselineDate
import buckpal.account.domain.Money
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.jdbc.datasource.init.ScriptUtils
import java.time.LocalDateTime
import javax.sql.DataSource

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SendMoneySystemTest : DescribeSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var loadAccountPort: LoadAccountPort

    @Autowired
    private lateinit var dataSource: DataSource

    private fun loadSql(resource: String) {
        val classpath = "buckpal/$resource"
        // Use DataSourceUtils so the script joins any active transaction,
        // matching Spring's annotation-driven SQL load semantics.
        // SpringBootTest does not auto-rollback, so inserts persist
        // for the lifetime of this test class's Spring context (the
        // file has one leaf, so no data-isolation issue arises).
        val connection = DataSourceUtils.getConnection(dataSource)
        try {
            ScriptUtils.executeSqlScript(connection, ClassPathResource(classpath))
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource)
        }
    }

    init {
        describe("POST /accounts/send/{sourceAccountId}/{targetAccountId}/{amount}") {
            it("sends money between two accounts") {
                loadSql("SendMoneySystemTest.sql")

                val initialSourceBalance = sourceAccount().calculateBalance()
                val initialTargetBalance = targetAccount().calculateBalance()

                val response = whenSendMoney(
                    sourceAccountId(),
                    targetAccountId(),
                    transferredAmount(),
                )

                response.statusCode shouldBe HttpStatus.OK

                sourceAccount().calculateBalance() shouldBe initialSourceBalance.minus(transferredAmount())

                targetAccount().calculateBalance() shouldBe initialTargetBalance.plus(transferredAmount())
            }
        }
    }

    private fun sourceAccount(): Account = loadAccount(sourceAccountId())

    private fun targetAccount(): Account = loadAccount(targetAccountId())

    private fun loadAccount(accountId: AccountId): Account =
        loadAccountPort.loadAccount(accountId, BaselineDate(LocalDateTime.now()))

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
