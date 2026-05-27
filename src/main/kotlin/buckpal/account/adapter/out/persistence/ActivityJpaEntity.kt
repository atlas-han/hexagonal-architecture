package buckpal.account.adapter.out.persistence

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "activity")
internal class ActivityJpaEntity(

    @Id
    @GeneratedValue
    var id: Long? = null,

    @Column
    var timestamp: LocalDateTime? = null,

    @Column
    var ownerAccountId: Long? = null,

    @Column
    var sourceAccountId: Long? = null,

    @Column
    var targetAccountId: Long? = null,

    @Column
    var amount: Long? = null,
)
