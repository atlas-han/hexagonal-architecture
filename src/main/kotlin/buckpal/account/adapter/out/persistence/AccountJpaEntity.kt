package buckpal.account.adapter.out.persistence

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "account")
internal class AccountJpaEntity(

    @Id
    @GeneratedValue
    var id: Long? = null,
)
