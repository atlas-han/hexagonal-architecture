package io.reflectoring.buckpal.account.adapter.out.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.reflectoring.buckpal.account.domain.Account
import io.reflectoring.buckpal.account.domain.Activity
import io.reflectoring.buckpal.account.domain.ActivityTimestamp
import io.reflectoring.buckpal.account.domain.BaselineBalanceFigures
import io.reflectoring.buckpal.account.domain.Money
import java.time.LocalDateTime

class AccountMapperTest : DescribeSpec({

    val mapper = AccountMapper()

    describe("mapToDomainEntity") {
        it("maps a JPA account + activities to a domain Account with baseline = deposit - withdrawal") {
            val account = AccountJpaEntity(id = 1L)
            val activities = mutableListOf(
                ActivityJpaEntity(
                    id = 10L,
                    timestamp = LocalDateTime.of(2020, 1, 1, 10, 0),
                    ownerAccountId = 1L,
                    sourceAccountId = 2L,
                    targetAccountId = 1L,
                    amount = 500L,
                ),
                ActivityJpaEntity(
                    id = 11L,
                    timestamp = LocalDateTime.of(2020, 1, 2, 10, 0),
                    ownerAccountId = 1L,
                    sourceAccountId = 1L,
                    targetAccountId = 2L,
                    amount = 100L,
                ),
            )

            val result = mapper.mapToDomainEntity(
                account = account,
                activities = activities,
                figures = BaselineBalanceFigures(
                    deposit = Money.of(700L),
                    withdrawal = Money.of(200L),
                ),
            )

            result.id shouldBe Account.AccountId(1L)
            result.baselineBalance shouldBe Money.of(500L)
            result.activityWindow.getActivities() shouldHaveSize 2
        }

        it("returns Account with empty ActivityWindow when activities list is empty") {
            val account = AccountJpaEntity(id = 7L)

            val result = mapper.mapToDomainEntity(
                account = account,
                activities = emptyList(),
                figures = BaselineBalanceFigures(
                    deposit = Money.of(0L),
                    withdrawal = Money.of(0L),
                ),
            )

            result.id shouldBe Account.AccountId(7L)
            result.baselineBalance shouldBe Money.of(0L)
            result.activityWindow.getActivities() shouldHaveSize 0
        }

        it("throws IllegalArgumentException when AccountJpaEntity.id is null") {
            val account = AccountJpaEntity(id = null)

            shouldThrow<IllegalArgumentException> {
                mapper.mapToDomainEntity(
                    account = account,
                    activities = emptyList(),
                    figures = BaselineBalanceFigures(
                        deposit = Money.of(0L),
                        withdrawal = Money.of(0L),
                    ),
                )
            }
        }
    }

    describe("mapToActivityWindow") {
        it("maps N activities preserving id, owner, source, target, timestamp, amount") {
            val ts1 = LocalDateTime.of(2021, 6, 1, 12, 0)
            val ts2 = LocalDateTime.of(2021, 6, 2, 12, 0)
            val activities = listOf(
                ActivityJpaEntity(
                    id = 100L,
                    timestamp = ts1,
                    ownerAccountId = 42L,
                    sourceAccountId = 42L,
                    targetAccountId = 41L,
                    amount = 1000L,
                ),
                ActivityJpaEntity(
                    id = 101L,
                    timestamp = ts2,
                    ownerAccountId = 41L,
                    sourceAccountId = 41L,
                    targetAccountId = 42L,
                    amount = 250L,
                ),
            )

            val window = mapper.mapToActivityWindow(activities)

            val mapped = window.getActivities()
            mapped shouldHaveSize 2

            val first = mapped[0]
            first.id shouldBe Activity.ActivityId(100L)
            first.ownerAccountId shouldBe Account.AccountId(42L)
            first.sourceAccountId shouldBe Account.AccountId(42L)
            first.targetAccountId shouldBe Account.AccountId(41L)
            first.timestamp shouldBe ActivityTimestamp(ts1)
            first.money shouldBe Money.of(1000L)

            val second = mapped[1]
            second.id shouldBe Activity.ActivityId(101L)
            second.ownerAccountId shouldBe Account.AccountId(41L)
            second.sourceAccountId shouldBe Account.AccountId(41L)
            second.targetAccountId shouldBe Account.AccountId(42L)
            second.timestamp shouldBe ActivityTimestamp(ts2)
            second.money shouldBe Money.of(250L)
        }

        it("returns an empty ActivityWindow when activities list is empty") {
            val window = mapper.mapToActivityWindow(emptyList())

            window.getActivities() shouldHaveSize 0
        }

        it("throws IllegalArgumentException when ActivityJpaEntity.id is null") {
            val activities = listOf(
                ActivityJpaEntity(
                    id = null,
                    timestamp = LocalDateTime.now(),
                    ownerAccountId = 1L,
                    sourceAccountId = 1L,
                    targetAccountId = 2L,
                    amount = 100L,
                ),
            )

            shouldThrow<IllegalArgumentException> {
                mapper.mapToActivityWindow(activities)
            }
        }

        it("throws IllegalArgumentException when ActivityJpaEntity.ownerAccountId is null") {
            val activities = listOf(
                ActivityJpaEntity(
                    id = 1L,
                    timestamp = LocalDateTime.now(),
                    ownerAccountId = null,
                    sourceAccountId = 1L,
                    targetAccountId = 2L,
                    amount = 100L,
                ),
            )

            shouldThrow<IllegalArgumentException> {
                mapper.mapToActivityWindow(activities)
            }
        }

        it("throws IllegalArgumentException when ActivityJpaEntity.sourceAccountId is null") {
            val activities = listOf(
                ActivityJpaEntity(
                    id = 1L,
                    timestamp = LocalDateTime.now(),
                    ownerAccountId = 1L,
                    sourceAccountId = null,
                    targetAccountId = 2L,
                    amount = 100L,
                ),
            )

            shouldThrow<IllegalArgumentException> {
                mapper.mapToActivityWindow(activities)
            }
        }

        it("throws IllegalArgumentException when ActivityJpaEntity.targetAccountId is null") {
            val activities = listOf(
                ActivityJpaEntity(
                    id = 1L,
                    timestamp = LocalDateTime.now(),
                    ownerAccountId = 1L,
                    sourceAccountId = 1L,
                    targetAccountId = null,
                    amount = 100L,
                ),
            )

            shouldThrow<IllegalArgumentException> {
                mapper.mapToActivityWindow(activities)
            }
        }

        it("throws IllegalArgumentException when ActivityJpaEntity.timestamp is null") {
            val activities = listOf(
                ActivityJpaEntity(
                    id = 1L,
                    timestamp = null,
                    ownerAccountId = 1L,
                    sourceAccountId = 1L,
                    targetAccountId = 2L,
                    amount = 100L,
                ),
            )

            shouldThrow<IllegalArgumentException> {
                mapper.mapToActivityWindow(activities)
            }
        }

        it("throws IllegalArgumentException when ActivityJpaEntity.amount is null") {
            val activities = listOf(
                ActivityJpaEntity(
                    id = 1L,
                    timestamp = LocalDateTime.now(),
                    ownerAccountId = 1L,
                    sourceAccountId = 1L,
                    targetAccountId = 2L,
                    amount = null,
                ),
            )

            shouldThrow<IllegalArgumentException> {
                mapper.mapToActivityWindow(activities)
            }
        }
    }

    describe("mapToJpaEntity") {
        it("round-trips an Activity into an ActivityJpaEntity preserving all fields") {
            val ts = LocalDateTime.of(2022, 3, 4, 9, 30)
            val activity = Activity(
                Activity.ActivityId(55L),
                Account.AccountId(10L),
                Account.AccountId(10L),
                Account.AccountId(20L),
                ActivityTimestamp(ts),
                Money.of(750L),
            )

            val jpa = mapper.mapToJpaEntity(activity)

            jpa.id shouldBe 55L
            jpa.timestamp shouldBe ts
            jpa.ownerAccountId shouldBe 10L
            jpa.sourceAccountId shouldBe 10L
            jpa.targetAccountId shouldBe 20L
            jpa.amount shouldBe 750L
        }

        it("maps an Activity with null id (new, not yet persisted) to a JPA entity with null id") {
            val ts = LocalDateTime.of(2022, 5, 6, 11, 0)
            val activity = Activity(
                Account.AccountId(1L),
                Account.AccountId(1L),
                Account.AccountId(2L),
                ActivityTimestamp(ts),
                Money.of(42L),
            )

            val jpa = mapper.mapToJpaEntity(activity)

            jpa.id shouldBe null
            jpa.timestamp shouldBe ts
            jpa.ownerAccountId shouldBe 1L
            jpa.sourceAccountId shouldBe 1L
            jpa.targetAccountId shouldBe 2L
            jpa.amount shouldBe 42L
        }
    }
})
