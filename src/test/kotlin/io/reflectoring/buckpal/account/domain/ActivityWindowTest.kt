package io.reflectoring.buckpal.account.domain

import io.reflectoring.buckpal.account.domain.Account.AccountId
import io.reflectoring.buckpal.common.ActivityTestData.defaultActivity
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ActivityWindowTest {

    @Test
    fun calculatesStartTimestamp() {
        val window = ActivityWindow(
            defaultActivity().withTimestamp(startDate()).build(),
            defaultActivity().withTimestamp(inBetweenDate()).build(),
            defaultActivity().withTimestamp(endDate()).build(),
        )

        Assertions.assertThat(window.getStartTimestamp()).isEqualTo(startDate())
    }

    @Test
    fun calculatesEndTimestamp() {
        val window = ActivityWindow(
            defaultActivity().withTimestamp(startDate()).build(),
            defaultActivity().withTimestamp(inBetweenDate()).build(),
            defaultActivity().withTimestamp(endDate()).build(),
        )

        Assertions.assertThat(window.getEndTimestamp()).isEqualTo(endDate())
    }

    @Test
    fun calculatesBalance() {
        val account1 = AccountId(1L)
        val account2 = AccountId(2L)

        val window = ActivityWindow(
            defaultActivity()
                .withSourceAccount(account1)
                .withTargetAccount(account2)
                .withMoney(Money.of(999L)).build(),
            defaultActivity()
                .withSourceAccount(account1)
                .withTargetAccount(account2)
                .withMoney(Money.of(1L)).build(),
            defaultActivity()
                .withSourceAccount(account2)
                .withTargetAccount(account1)
                .withMoney(Money.of(500L)).build(),
        )

        Assertions.assertThat(window.calculateBalance(account1)).isEqualTo(Money.of(-500L))
        Assertions.assertThat(window.calculateBalance(account2)).isEqualTo(Money.of(500L))
    }

    private fun startDate(): LocalDateTime = LocalDateTime.of(2019, 8, 3, 0, 0)

    private fun inBetweenDate(): LocalDateTime = LocalDateTime.of(2019, 8, 4, 0, 0)

    private fun endDate(): LocalDateTime = LocalDateTime.of(2019, 8, 5, 0, 0)
}
