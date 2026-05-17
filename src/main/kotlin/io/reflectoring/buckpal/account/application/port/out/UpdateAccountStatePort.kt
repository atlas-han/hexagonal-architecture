package io.reflectoring.buckpal.account.application.port.out

import io.reflectoring.buckpal.account.domain.Account

interface UpdateAccountStatePort {

    fun updateActivities(account: Account)
}
