package buckpal.account.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository

internal interface SpringDataAccountRepository : JpaRepository<AccountJpaEntity, Long>
