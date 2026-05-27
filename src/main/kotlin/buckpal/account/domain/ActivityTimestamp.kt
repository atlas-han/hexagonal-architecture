package buckpal.account.domain

import java.time.LocalDateTime

/**
 * The instant at which an [Activity] occurred. Distinct from [BaselineDate],
 * which is the cutoff dividing the baseline balance from the activity window.
 * The persistence layer ([buckpal.account.adapter.out.persistence.ActivityJpaEntity])
 * still stores the value as a raw [LocalDateTime]; conversion happens at the
 * [buckpal.account.adapter.out.persistence.AccountMapper] boundary.
 */
@JvmInline
value class ActivityTimestamp(val value: LocalDateTime) {

    companion object {
        fun now(): ActivityTimestamp = ActivityTimestamp(LocalDateTime.now())
    }
}
