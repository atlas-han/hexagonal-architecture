package io.reflectoring.buckpal

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.core.spec.style.FunSpec
import io.reflectoring.buckpal.archunit.HexagonalArchitecture

class DependencyRuleTests : FunSpec({

    test("validateRegistrationContextArchitecture") {
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

    test("testPackageDependencies") {
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
})
