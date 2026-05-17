package io.reflectoring.buckpal.account.adapter.out.persistence

import io.reflectoring.buckpal.account.domain.Account.AccountId
import io.reflectoring.buckpal.account.domain.ActivityWindow
import io.reflectoring.buckpal.account.domain.Money
import io.reflectoring.buckpal.common.AccountTestData.defaultAccount
import io.reflectoring.buckpal.common.ActivityTestData.defaultActivity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDateTime

@DataJpaTest
@Import(AccountPersistenceAdapter::class, AccountMapper::class)
class AccountPersistenceAdapterTest {

    @Autowired
    private lateinit var adapterUnderTest: AccountPersistenceAdapter

    @Autowired
    private lateinit var activityRepository: ActivityRepository

    @Test
    @Sql("AccountPersistenceAdapterTest.sql")
    fun loadsAccount() {
        val account = adapterUnderTest.loadAccount(AccountId(1L), LocalDateTime.of(2018, 8, 10, 0, 0))

        assertThat(account.activityWindow.getActivities()).hasSize(2)
        assertThat(account.calculateBalance()).isEqualTo(Money.of(500L))
    }

    @Test
    fun updatesActivities() {
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

        assertThat(activityRepository.count()).isEqualTo(1)

        val savedActivity = activityRepository.findAll().get(0)
        assertThat(savedActivity.amount).isEqualTo(1L)
    }
}
