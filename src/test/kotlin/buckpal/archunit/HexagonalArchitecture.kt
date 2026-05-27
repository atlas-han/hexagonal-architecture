package io.reflectoring.buckpal.archunit

import com.tngtech.archunit.core.domain.JavaClasses

internal class HexagonalArchitecture(basePackage: String) : ArchitectureElement(basePackage) {

    private var adapters: Adapters? = null
    private var applicationLayer: ApplicationLayer? = null
    private var configurationPackage: String? = null
    private val domainPackages: MutableList<String> = mutableListOf()

    fun withAdaptersLayer(adaptersPackage: String): Adapters {
        val newAdapters = Adapters(this, fullQualifiedPackage(adaptersPackage))
        adapters = newAdapters
        return newAdapters
    }

    fun withDomainLayer(domainPackage: String): HexagonalArchitecture {
        domainPackages.add(fullQualifiedPackage(domainPackage))
        return this
    }

    fun withApplicationLayer(applicationPackage: String): ApplicationLayer {
        val newApplicationLayer = ApplicationLayer(fullQualifiedPackage(applicationPackage), this)
        applicationLayer = newApplicationLayer
        return newApplicationLayer
    }

    fun withConfiguration(packageName: String): HexagonalArchitecture {
        configurationPackage = fullQualifiedPackage(packageName)
        return this
    }

    private fun domainDoesNotDependOnOtherPackages(
        adapters: Adapters,
        applicationLayer: ApplicationLayer,
        classes: JavaClasses,
    ) {
        denyAnyDependency(domainPackages, listOf(adapters.basePackage), classes)
        denyAnyDependency(domainPackages, listOf(applicationLayer.basePackage), classes)
    }

    fun check(classes: JavaClasses) {
        val adapters = checkNotNull(adapters) {
            "withAdaptersLayer must be called before check()"
        }
        val applicationLayer = checkNotNull(applicationLayer) {
            "withApplicationLayer must be called before check()"
        }
        val configurationPackage = checkNotNull(configurationPackage) {
            "withConfiguration must be called before check()"
        }
        adapters.doesNotContainEmptyPackages()
        adapters.dontDependOnEachOther(classes)
        adapters.doesNotDependOn(configurationPackage, classes)
        applicationLayer.doesNotContainEmptyPackages()
        applicationLayer.doesNotDependOn(adapters.basePackage, classes)
        applicationLayer.doesNotDependOn(configurationPackage, classes)
        applicationLayer.incomingAndOutgoingPortsDoNotDependOnEachOther(classes)
        domainDoesNotDependOnOtherPackages(adapters, applicationLayer, classes)
    }

    companion object {
        @JvmStatic
        fun boundedContext(basePackage: String): HexagonalArchitecture =
            HexagonalArchitecture(basePackage)
    }
}
