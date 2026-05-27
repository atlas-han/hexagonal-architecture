package io.reflectoring.buckpal.account.application.service

import io.reflectoring.buckpal.account.domain.Money

class ThresholdExceededException(threshold: Money, actual: Money) : RuntimeException(
    "Maximum threshold for transferring money exceeded: tried to transfer $actual but threshold is $threshold!"
)
