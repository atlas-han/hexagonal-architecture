package io.reflectoring.buckpal.account.domain

import java.time.LocalDateTime

@JvmInline
value class BaselineDate(val value: LocalDateTime) {

    fun minusDays(days: Long): BaselineDate = BaselineDate(value.minusDays(days))

    companion object {
        fun now(): BaselineDate = BaselineDate(LocalDateTime.now())
    }
}
