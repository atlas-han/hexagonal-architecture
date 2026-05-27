package io.reflectoring.buckpal.archunit

import com.tngtech.archunit.core.domain.JavaClasses

internal class ApplicationLayer(
    basePackage: String,
    private val parentContext: HexagonalArchitecture,
) : ArchitectureElement(basePackage) {

    private val incomingPortsPackages: MutableList<String> = mutableListOf()
    private val outgoingPortsPackages: MutableList<String> = mutableListOf()
    private val servicePackages: MutableList<String> = mutableListOf()

    fun incomingPorts(packageName: String): ApplicationLayer {
        incomingPortsPackages.add(fullQualifiedPackage(packageName))
        return this
    }

    fun outgoingPorts(packageName: String): ApplicationLayer {
        outgoingPortsPackages.add(fullQualifiedPackage(packageName))
        return this
    }

    fun services(packageName: String): ApplicationLayer {
        servicePackages.add(fullQualifiedPackage(packageName))
        return this
    }

    fun and(): HexagonalArchitecture = parentContext

    fun doesNotDependOn(packageName: String, classes: JavaClasses) {
        denyDependency(basePackage, packageName, classes)
    }

    fun incomingAndOutgoingPortsDoNotDependOnEachOther(classes: JavaClasses) {
        denyAnyDependency(incomingPortsPackages, outgoingPortsPackages, classes)
        denyAnyDependency(outgoingPortsPackages, incomingPortsPackages, classes)
    }

    private fun allPackages(): List<String> {
        val allPackages = mutableListOf<String>()
        allPackages.addAll(incomingPortsPackages)
        allPackages.addAll(outgoingPortsPackages)
        allPackages.addAll(servicePackages)
        return allPackages
    }

    internal fun doesNotContainEmptyPackages() {
        denyEmptyPackages(allPackages())
    }
}
