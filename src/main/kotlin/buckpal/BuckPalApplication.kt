package io.reflectoring.buckpal

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BuckPalApplication

fun main(args: Array<String>) {
    runApplication<BuckPalApplication>(*args)
}
