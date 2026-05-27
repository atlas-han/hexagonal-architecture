package buckpal

import com.tngtech.archunit.core.importer.ClassFileImporter
import io.kotest.core.spec.style.FunSpec
import buckpal.archunit.HexagonalArchitecture

class DependencyRuleTests : FunSpec({

    test("validateRegistrationContextArchitecture") {
        HexagonalArchitecture.boundedContext("buckpal.account")

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
                    .importPackages("buckpal.."),
            )
    }
})
