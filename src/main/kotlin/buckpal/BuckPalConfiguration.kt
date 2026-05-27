package buckpal

import buckpal.account.application.service.MoneyTransferProperties
import buckpal.account.domain.Money
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration that exposes use-case-specific beans derived from
 * the Spring-Boot-bound [BuckPalConfigurationProperties].
 */
@Configuration
@EnableConfigurationProperties(BuckPalConfigurationProperties::class)
class BuckPalConfiguration {

    /**
     * Adds a use-case-specific [MoneyTransferProperties] bean to the
     * application context. The properties are read from the Spring-Boot-
     * specific [BuckPalConfigurationProperties] object.
     */
    @Bean
    fun moneyTransferProperties(
        buckPalConfigurationProperties: BuckPalConfigurationProperties,
    ): MoneyTransferProperties =
        MoneyTransferProperties(
            Money.of(buckPalConfigurationProperties.transferThreshold),
        )
}
