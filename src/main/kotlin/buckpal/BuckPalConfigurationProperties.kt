package buckpal

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "buckpal")
data class BuckPalConfigurationProperties(
    var transferThreshold: Long = Long.MAX_VALUE,
)
