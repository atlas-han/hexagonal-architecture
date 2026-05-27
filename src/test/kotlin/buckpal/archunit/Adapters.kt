package buckpal.archunit

import com.tngtech.archunit.core.domain.JavaClasses

internal class Adapters internal constructor(
    private val parentContext: HexagonalArchitecture,
    basePackage: String,
) : ArchitectureElement(basePackage) {

    private val incomingAdapterPackages: MutableList<String> = mutableListOf()
    private val outgoingAdapterPackages: MutableList<String> = mutableListOf()

    fun outgoing(packageName: String): Adapters {
        incomingAdapterPackages.add(fullQualifiedPackage(packageName))
        return this
    }

    fun incoming(packageName: String): Adapters {
        outgoingAdapterPackages.add(fullQualifiedPackage(packageName))
        return this
    }

    internal fun allAdapterPackages(): List<String> {
        val allAdapters = mutableListOf<String>()
        allAdapters.addAll(incomingAdapterPackages)
        allAdapters.addAll(outgoingAdapterPackages)
        return allAdapters
    }

    fun and(): HexagonalArchitecture = parentContext

    internal fun dontDependOnEachOther(classes: JavaClasses) {
        val allAdapters = allAdapterPackages()
        for (adapter1 in allAdapters) {
            for (adapter2 in allAdapters) {
                if (adapter1 != adapter2) {
                    denyDependency(adapter1, adapter2, classes)
                }
            }
        }
    }

    internal fun doesNotDependOn(packageName: String, classes: JavaClasses) {
        denyDependency(basePackage, packageName, classes)
    }

    internal fun doesNotContainEmptyPackages() {
        denyEmptyPackages(allAdapterPackages())
    }
}
