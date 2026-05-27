package buckpal.account.application.service

import buckpal.account.application.port.`in`.SendMoneyCommand
import buckpal.account.application.port.`in`.SendMoneyUseCase
import buckpal.account.application.port.out.AccountLock
import buckpal.account.application.port.out.LoadAccountPort
import buckpal.account.application.port.out.UpdateAccountStatePort
import buckpal.account.domain.BaselineDate
import buckpal.common.UseCase
import javax.transaction.Transactional

@UseCase
@Transactional
class SendMoneyService(
    private val loadAccountPort: LoadAccountPort,
    private val accountLock: AccountLock,
    private val updateAccountStatePort: UpdateAccountStatePort,
    private val moneyTransferProperties: MoneyTransferProperties,
) : SendMoneyUseCase {

    override fun sendMoney(command: SendMoneyCommand): Boolean {
        checkThreshold(command)

        val baselineDate = BaselineDate.now().minusDays(10)

        val sourceAccount = loadAccountPort.loadAccount(command.sourceAccountId, baselineDate)
        val targetAccount = loadAccountPort.loadAccount(command.targetAccountId, baselineDate)

        val sourceAccountId = sourceAccount.id
            ?: error("expected source account ID not to be empty")
        val targetAccountId = targetAccount.id
            ?: error("expected target account ID not to be empty")

        accountLock.lockAccount(sourceAccountId)
        if (!sourceAccount.withdraw(command.money, targetAccountId)) {
            accountLock.releaseAccount(sourceAccountId)
            return false
        }

        accountLock.lockAccount(targetAccountId)
        if (!targetAccount.deposit(command.money, sourceAccountId)) {
            accountLock.releaseAccount(sourceAccountId)
            accountLock.releaseAccount(targetAccountId)
            return false
        }

        updateAccountStatePort.updateActivities(sourceAccount)
        updateAccountStatePort.updateActivities(targetAccount)

        accountLock.releaseAccount(sourceAccountId)
        accountLock.releaseAccount(targetAccountId)
        return true
    }

    private fun checkThreshold(command: SendMoneyCommand) {
        if (command.money.isGreaterThan(moneyTransferProperties.maximumTransferThreshold)) {
            throw ThresholdExceededException(moneyTransferProperties.maximumTransferThreshold, command.money)
        }
    }
}
