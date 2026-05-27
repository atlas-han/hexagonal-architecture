package buckpal.account.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import buckpal.account.domain.Account.AccountId
import buckpal.common.AccountTestData.defaultAccount
import buckpal.common.ActivityTestData.defaultActivity

class AccountTest : BehaviorSpec({

    given("an account with baseline 555 and two deposit activities of 999 and 1") {
        `when`("calculating the balance") {
            then("calculates balance") {
                val accountId = AccountId(1L)
                val account = defaultAccount()
                    .withAccountId(accountId)
                    .withBaselineBalance(Money.of(555L))
                    .withActivityWindow(
                        ActivityWindow(
                            defaultActivity()
                                .withTargetAccount(accountId)
                                .withMoney(Money.of(999L)).build(),
                            defaultActivity()
                                .withTargetAccount(accountId)
                                .withMoney(Money.of(1L)).build(),
                        ),
                    )
                    .build()

                val balance = account.calculateBalance()

                balance shouldBe Money.of(1555L)
            }
        }

        `when`("withdrawing 555 with sufficient funds") {
            then("withdrawal succeeds") {
                val accountId = AccountId(1L)
                val account = defaultAccount()
                    .withAccountId(accountId)
                    .withBaselineBalance(Money.of(555L))
                    .withActivityWindow(
                        ActivityWindow(
                            defaultActivity()
                                .withTargetAccount(accountId)
                                .withMoney(Money.of(999L)).build(),
                            defaultActivity()
                                .withTargetAccount(accountId)
                                .withMoney(Money.of(1L)).build(),
                        ),
                    )
                    .build()

                val success = account.withdraw(Money.of(555L), AccountId(99L))

                success.shouldBeTrue()
                account.activityWindow.getActivities() shouldHaveSize 3
                account.calculateBalance() shouldBe Money.of(1000L)
            }
        }

        `when`("withdrawing more than the available balance") {
            then("withdrawal fails when insufficient funds") {
                val accountId = AccountId(1L)
                val account = defaultAccount()
                    .withAccountId(accountId)
                    .withBaselineBalance(Money.of(555L))
                    .withActivityWindow(
                        ActivityWindow(
                            defaultActivity()
                                .withTargetAccount(accountId)
                                .withMoney(Money.of(999L)).build(),
                            defaultActivity()
                                .withTargetAccount(accountId)
                                .withMoney(Money.of(1L)).build(),
                        ),
                    )
                    .build()

                val success = account.withdraw(Money.of(1556L), AccountId(99L))

                success.shouldBeFalse()
                account.activityWindow.getActivities() shouldHaveSize 2
                account.calculateBalance() shouldBe Money.of(1555L)
            }
        }

        `when`("depositing 445") {
            then("deposit succeeds") {
                val accountId = AccountId(1L)
                val account = defaultAccount()
                    .withAccountId(accountId)
                    .withBaselineBalance(Money.of(555L))
                    .withActivityWindow(
                        ActivityWindow(
                            defaultActivity()
                                .withTargetAccount(accountId)
                                .withMoney(Money.of(999L)).build(),
                            defaultActivity()
                                .withTargetAccount(accountId)
                                .withMoney(Money.of(1L)).build(),
                        ),
                    )
                    .build()

                val success = account.deposit(Money.of(445L), AccountId(99L))

                success.shouldBeTrue()
                account.activityWindow.getActivities() shouldHaveSize 3
                account.calculateBalance() shouldBe Money.of(2000L)
            }
        }
    }
})
