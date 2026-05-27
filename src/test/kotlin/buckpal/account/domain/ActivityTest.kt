package buckpal.account.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import buckpal.account.domain.Account.AccountId
import buckpal.account.domain.Activity.ActivityId
import java.time.LocalDateTime

class ActivityTest : BehaviorSpec({

    val owner = AccountId(1L)
    val source = AccountId(2L)
    val target = AccountId(3L)
    val timestamp = ActivityTimestamp(LocalDateTime.of(2024, 1, 1, 12, 0))
    val money = Money.of(500L)

    given("the Activity secondary constructor (without id)") {
        `when`("constructing an Activity without an id") {
            val activity = Activity(owner, source, target, timestamp, money)

            then("the id is null") {
                activity.id.shouldBeNull()
            }
            then("the other properties are wired through") {
                activity.ownerAccountId shouldBe owner
                activity.sourceAccountId shouldBe source
                activity.targetAccountId shouldBe target
                activity.timestamp shouldBe timestamp
                activity.money shouldBe money
            }
        }
    }

    given("the Activity primary constructor (with id)") {
        val id = ActivityId(42L)
        val activity = Activity(id, owner, source, target, timestamp, money)

        `when`("inspecting properties") {
            then("the id is set") {
                activity.id shouldBe id
            }
        }

        `when`("comparing equal instances") {
            val same = Activity(id, owner, source, target, timestamp, money)
            then("they are equal and share a hashCode") {
                activity shouldBe same
                activity.hashCode() shouldBe same.hashCode()
            }
            then("toString contains the id value") {
                activity.toString().contains("id=ActivityId(value=42)") shouldBe true
            }
        }

        `when`("comparing to a different Activity") {
            val different = Activity(ActivityId(99L), owner, source, target, timestamp, money)
            then("they are not equal") {
                activity shouldNotBe different
            }
        }

        `when`("calling copy") {
            then("produces an equal Activity when arguments are unchanged") {
                activity.copy() shouldBe activity
            }
            then("produces a different Activity when an argument changes") {
                val copied = activity.copy(money = Money.of(1L))
                copied shouldNotBe activity
                copied.money shouldBe Money.of(1L)
            }
        }
    }

    given("the ActivityId data class") {
        val a = ActivityId(7L)
        val b = ActivityId(7L)
        val c = ActivityId(8L)

        `when`("comparing equal ActivityIds") {
            then("they are equal and share a hashCode") {
                a shouldBe b
                a.hashCode() shouldBe b.hashCode()
            }
        }

        `when`("comparing different ActivityIds") {
            then("they are not equal") {
                a shouldNotBe c
            }
        }

        `when`("calling toString") {
            then("contains the value") {
                a.toString() shouldBe "ActivityId(value=7)"
            }
        }

        `when`("calling copy") {
            then("returns an equal instance when unchanged") {
                a.copy() shouldBe a
            }
            then("returns a new instance with the changed value") {
                a.copy(value = 42L) shouldBe ActivityId(42L)
            }
        }
    }
})
