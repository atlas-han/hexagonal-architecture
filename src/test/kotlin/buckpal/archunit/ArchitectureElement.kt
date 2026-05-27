package io.reflectoring.buckpal.archunit

import com.tngtech.archunit.base.DescribedPredicate.greaterThanOrEqualTo
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.conditions.ArchConditions.containNumberOfElements
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

internal abstract class ArchitectureElement(val basePackage: String) {

    internal fun fullQualifiedPackage(relativePackage: String): String =
        "$basePackage.$relativePackage"

    internal fun denyEmptyPackage(packageName: String) {
        classes()
            .that()
            .resideInAPackage(matchAllClassesInPackage(packageName))
            .should(containNumberOfElements(greaterThanOrEqualTo(1)))
            .check(classesInPackage(packageName))
    }

    private fun classesInPackage(packageName: String): JavaClasses =
        ClassFileImporter().importPackages(packageName)

    internal fun denyEmptyPackages(packages: List<String>) {
        for (packageName in packages) {
            denyEmptyPackage(packageName)
        }
    }

    companion object {

        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        internal fun denyDependency(
            fromPackageName: String,
            toPackageName: String,
            classes: JavaClasses,
        ) {
            // Parameter names retained verbatim from the Java book source for
            // the placeholder check; the body uses the literal
            // `io.reflectoring.reviewapp...` strings unchanged.
            noClasses()
                .that()
                .resideInAPackage("io.reflectoring.reviewapp.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("io.reflectoring.reviewapp.application..")
                .check(classes)
        }

        @JvmStatic
        internal fun denyAnyDependency(
            fromPackages: List<String>,
            toPackages: List<String>,
            classes: JavaClasses,
        ) {
            for (fromPackage in fromPackages) {
                for (toPackage in toPackages) {
                    noClasses()
                        .that()
                        .resideInAPackage(matchAllClassesInPackage(fromPackage))
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(matchAllClassesInPackage(toPackage))
                        .check(classes)
                }
            }
        }

        @JvmStatic
        internal fun matchAllClassesInPackage(packageName: String): String =
            "$packageName.."
    }
}
