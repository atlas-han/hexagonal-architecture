# kotlin-conversion-context — Examples

## Example 1: Money 값 클래스 변환

### Before (Java + Lombok)
```java
@Value
public class Money {
    private final BigDecimal amount;

    public static Money of(long value) {
        return new Money(BigDecimal.valueOf(value));
    }

    public Money add(Money money) {
        return new Money(this.amount.add(money.amount));
    }

    public boolean isPositiveOrZero() {
        return this.amount.compareTo(BigDecimal.ZERO) >= 0;
    }

    public boolean isNegative() {
        return this.amount.compareTo(BigDecimal.ZERO) < 0;
    }
}
```

### After (Kotlin)
```kotlin
data class Money(val amount: BigDecimal) {

    // BigDecimal.equals is scale-sensitive (10.0 != 10.00).
    // Normalize on construction so equals/hashCode work correctly.
    init {
        require(amount.scale() <= 2) { "scale must be <= 2" }
    }

    operator fun plus(money: Money): Money = Money(amount.add(money.amount))

    fun isPositiveOrZero(): Boolean = amount >= BigDecimal.ZERO

    fun isNegative(): Boolean = amount < BigDecimal.ZERO

    companion object {
        fun of(value: Long): Money = Money(BigDecimal.valueOf(value))
    }
}
```

**Key pitfall**: `data class` uses `BigDecimal.equals`, which is scale-sensitive.
Without normalization, `Money.of(10)` vs `Money(BigDecimal("10.00"))` won't be equal.

---

## Example 2: Spring @Component 클래스 변환

### Before (Java + Lombok)
```java
@UseCase
@RequiredArgsConstructor
@Transactional
public class SendMoneyService implements SendMoneyUseCase {
    private final LoadAccountPort loadAccountPort;
    private final AccountLock accountLock;
    private final UpdateAccountStatePort updateAccountStatePort;

    @Override
    public boolean sendMoney(SendMoneyCommand command) { ... }
}
```

### After (Kotlin)
```kotlin
@UseCase
@Transactional
class SendMoneyService(
    private val loadAccountPort: LoadAccountPort,
    private val accountLock: AccountLock,
    private val updateAccountStatePort: UpdateAccountStatePort,
) : SendMoneyUseCase {

    override fun sendMoney(command: SendMoneyCommand): Boolean { ... }
}
```

**Key note**: `kotlin-spring` plugin auto-opens `@UseCase` classes. Confirm it is added
in sprint-00 or Spring AOP will fail at runtime.

---

## Example 3: JPA Entity 변환

### Before (Java + Lombok)
```java
@Entity
@Table(name = "account")
@Data
@AllArgsConstructor
@NoArgsConstructor
class AccountJpaEntity {
    @Id
    @GeneratedValue
    private Long id;
}
```

### After (Kotlin)
```kotlin
@Entity
@Table(name = "account")
class AccountJpaEntity(
    @Id
    @GeneratedValue
    val id: Long? = null,
)
```

**Key note**: `kotlin-jpa` plugin synthesizes the no-arg constructor Hibernate needs.
Without it, `AccountJpaEntity` cannot be instantiated by the persistence provider.

---

## Example 4: SelfValidating 커맨드 객체

### Before (Java)
```java
@Value
public class SendMoneyCommand extends SelfValidating<SendMoneyCommand> {
    @NotNull private final AccountId sourceAccountId;
    @NotNull private final AccountId targetAccountId;
    @NotNull @Positive private final Money money;

    public SendMoneyCommand(...) {
        this.sourceAccountId = sourceAccountId;
        ...
        this.validateSelf();
    }
}
```

### After (Kotlin)
```kotlin
data class SendMoneyCommand(
    @field:NotNull val sourceAccountId: AccountId,
    @field:NotNull val targetAccountId: AccountId,
    @field:NotNull @field:Positive val money: Money,
) : SelfValidating<SendMoneyCommand>() {
    init { validateSelf() }
}
```

**Key note**: Use `@field:NotNull` (not `@NotNull`) so Bean Validation sees the
annotation on the backing field, not the constructor parameter.
