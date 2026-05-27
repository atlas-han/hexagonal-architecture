package buckpal.account.application.port.out

import buckpal.account.domain.Account

interface UpdateAccountStatePort {

    fun updateActivities(account: Account)
}
