package io.reflectoring.buckpal

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.reflectoring.buckpal.archunit.HexagonalArchitecture
import org.junit.jupiter.api.Test

class DependencyRuleTests {

    @Test
    fun validateRegistrationContextArchitecture() {
        HexagonalArchitecture.boundedContext("io.reflectoring.buckpal.account")

            .withDomainLayer("domain")

            .withAdaptersLayer("adapter")
            .incoming("in.web")
            .outgoing("out.persistence")
            .and()

            .withApplicationLayer("application")
            .services("service")
            .incomingPorts("port.in")
            .outgoingPorts("port.out")
            .and()

            .withConfiguration("configuration")
            .check(
                ClassFileImporter()
                    .importPackages("io.reflectoring.buckpal.."),
            )
    }

    @Test
    fun testPackageDependencies() {
        noClasses()
            .that()
            .resideInAPackage("io.reflectoring.reviewapp.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.reflectoring.reviewapp.application..")
            .check(
                ClassFileImporter()
                    .importPackages("io.reflectoring.reviewapp.."),
            )
    }
}
