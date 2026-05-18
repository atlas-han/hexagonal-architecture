package io.reflectoring.buckpal.account.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.reflectoring.buckpal.account.domain.Account.AccountId
import io.reflectoring.buckpal.common.ActivityTestData.defaultActivity
import java.time.LocalDateTime

class ActivityWindowTest : BehaviorSpec({

    val startDate: () -> LocalDateTime = { LocalDateTime.of(2019, 8, 3, 0, 0) }
    val inBetweenDate: () -> LocalDateTime = { LocalDateTime.of(2019, 8, 4, 0, 0) }
    val endDate: () -> LocalDateTime = { LocalDateTime.of(2019, 8, 5, 0, 0) }

    given("an ActivityWindow with three activities") {
        `when`("calculating the start timestamp") {
            then("calculates start timestamp") {
                val window = ActivityWindow(
                    defaultActivity().withTimestamp(startDate()).build(),
                    defaultActivity().withTimestamp(inBetweenDate()).build(),
                    defaultActivity().withTimestamp(endDate()).build(),
                )

                window.getStartTimestamp() shouldBe startDate()
            }
        }

        `when`("calculating the end timestamp") {
            then("calculates end timestamp") {
                val window = ActivityWindow(
                    defaultActivity().withTimestamp(startDate()).build(),
                    defaultActivity().withTimestamp(inBetweenDate()).build(),
                    defaultActivity().withTimestamp(endDate()).build(),
                )

                window.getEndTimestamp() shouldBe endDate()
            }
        }

        `when`("calculating the per-account balance with transfers between two accounts") {
            then("calculates per-account balance") {
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

                window.calculateBalance(account1) shouldBe Money.of(-500L)
                window.calculateBalance(account2) shouldBe Money.of(500L)
            }
        }
    }
})
