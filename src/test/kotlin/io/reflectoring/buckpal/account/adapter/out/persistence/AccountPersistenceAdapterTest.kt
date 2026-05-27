package io.reflectoring.buckpal.account.adapter.out.persistence

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.reflectoring.buckpal.account.domain.Account.AccountId
import io.reflectoring.buckpal.account.domain.ActivityWindow
import io.reflectoring.buckpal.account.domain.BaselineDate
import io.reflectoring.buckpal.account.domain.Money
import io.reflectoring.buckpal.common.AccountTestData.defaultAccount
import io.reflectoring.buckpal.common.ActivityTestData.defaultActivity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.jdbc.datasource.init.ScriptUtils
import java.time.LocalDateTime
import javax.sql.DataSource

@DataJpaTest
@Import(AccountPersistenceAdapter::class, AccountMapper::class)
class AccountPersistenceAdapterTest : DescribeSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    private lateinit var adapterUnderTest: AccountPersistenceAdapter

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Autowired
    private lateinit var dataSource: DataSource

    private fun loadSql(resource: String) {
        val classpath = "io/reflectoring/buckpal/account/adapter/out/persistence/$resource"
        // Use DataSourceUtils so the script joins the test's active transaction,
        // matching Spring's @Sql semantics (rolled back at leaf end by @DataJpaTest).
        val connection = DataSourceUtils.getConnection(dataSource)
        try {
            ScriptUtils.executeSqlScript(connection, ClassPathResource(classpath))
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource)
        }
    }

    init {
        describe("AccountPersistenceAdapter") {
            it("loads account") {
                loadSql("AccountPersistenceAdapterTest.sql")

                val account = adapterUnderTest.loadAccount(AccountId(1L), BaselineDate(LocalDateTime.of(2018, 8, 10, 0, 0)))

                account.activityWindow.getActivities() shouldHaveSize 2
                account.calculateBalance() shouldBe Money.of(500L)
            }

            it("updates activities") {
                val account = defaultAccount()
                    .withBaselineBalance(Money.of(555L))
                    .withActivityWindow(
                        ActivityWindow(
                            defaultActivity()
                                .withId(null)
                                .withMoney(Money.of(1L)).build(),
                        ),
                    )
                    .build()

                adapterUnderTest.updateActivities(account)

                activityRepository.count() shouldBe 1L

                val savedActivity = activityRepository.findAll().get(0)
                savedActivity.amount shouldBe 1L
            }
        }
    }
}
