// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

package dev.pushport.sdk

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.junit.Test

/** Rules inspect bytecode, including dependencies introduced through signatures and annotations. */
class ArchitectureTest {
    private val classes =
        ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.pushport.sdk.internal")

    @Test fun `core models and ports remain independent of Android and adapters`() {
        noClasses()
            .that()
            .resideInAnyPackage(
                "dev.pushport.sdk.internal.core..",
                "dev.pushport.sdk.internal.model..",
                "dev.pushport.sdk.internal.ports..",
            ).should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "android..",
                "androidx..",
                "com.google..",
                "org.json..",
                "dev.pushport.sdk.internal.firebase..",
                "dev.pushport.sdk.internal.platform..",
                "dev.pushport.sdk.internal.network..",
                "dev.pushport.sdk.internal.storage..",
                "dev.pushport.sdk.internal.serialization..",
                "dev.pushport.sdk.internal.runtime..",
            ).because("the synchronization rules must be testable without a device, Firebase or persistence")
            .check(classes)
    }

    @Test fun `internal packages have no dependency cycles`() {
        slices()
            .matching("dev.pushport.sdk.internal.(*)..")
            .should()
            .beFreeOfCycles()
            .check(classes)
    }

    @Test fun `models and ports never depend on orchestration`() {
        noClasses()
            .that()
            .resideInAnyPackage("dev.pushport.sdk.internal.model..", "dev.pushport.sdk.internal.ports..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("dev.pushport.sdk.internal.core..")
            .check(classes)
    }
}
