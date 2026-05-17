package io.reflectoring.buckpal.account.application.service

import io.reflectoring.buckpal.account.application.port.out.AccountLock
import io.reflectoring.buckpal.account.domain.Account
import org.springframework.stereotype.Component

@Component
internal class NoOpAccountLock : AccountLock {

    override fun lockAccount(accountId: Account.AccountId) {
        // do nothing
    }

    override fun releaseAccount(accountId: Account.AccountId) {
        // do nothing
    }
}
