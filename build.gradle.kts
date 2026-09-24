// SPDX-FileCopyrightText: 2026 Oleh Yurkov
// SPDX-License-Identifier: Apache-2.0

import com.android.build.api.artifact.SingleArtifact
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import java.net.URI

plugins {
    id("com.android.library")
    id("org.jetbrains.dokka") version "2.2.0"
    id("com.vanniktech.maven.publish.base") version "0.37.0"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1" apply false
    `maven-publish`
}

abstract class SyncLicenseResources : Sync() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
}

val sdkLicenseFiles = files("LICENSE", "NOTICE")
val sdkLicensePath = "META-INF/dev.pushport/android-sdk"

// Keep notices in classes.jar under an SDK-specific path to avoid consumer merge conflicts.
androidComponents.onVariants { variant ->
    val licenseResources =
        tasks.register<SyncLicenseResources>("generate${variant.name.replaceFirstChar { it.uppercase() }}LicenseResources") {
            from(sdkLicenseFiles) { into(sdkLicensePath) }
            into(outputDirectory)
        }
    variant.sources.resources?.addGeneratedSourceDirectory(licenseResources, SyncLicenseResources::outputDirectory)
}

group = providers.gradleProperty("pushport.group").getOrElse("dev.pushport")
version = "0.0.2"
val serviceUrl = providers.gradleProperty("pushport.serverUrl").getOrElse("https://pushport.dev").trimEnd('/')
require(
    serviceUrl.isEmpty() ||
        runCatching {
            val uri = URI(serviceUrl)
            uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null
        }.getOrDefault(false),
) { "pushport.serverUrl must be an absolute HTTPS URL" }
android {
    namespace = "dev.pushport.sdk"
    resourcePrefix = "pushport_"
    compileSdk = 36
    buildFeatures { buildConfig = true }
    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"$serviceUrl\"")
        buildConfigField("String", "SDK_VERSION", "\"${project.version}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    publishing { singleVariant("release") { withSourcesJar() } }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
kotlin {
    explicitApi()
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// AGP 9's built-in Kotlin does not trigger BCV's legacy kotlin-android integration.
// Inspect classes.jar from the actual release AAR through the supported Android artifact API.
val abiValidator by configurations.creating { isCanBeConsumed = false }
dependencies {
    abiValidator("org.ow2.asm:asm-tree:9.8")
    abiValidator("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.21")
}
androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    val unpackApiJar =
        tasks.register<Sync>("unpackApiJar") {
            from(variant.artifacts.get(SingleArtifact.AAR).map { zipTree(it.asFile) })
            include("classes.jar")
            into(layout.buildDirectory.dir("api/classes"))
        }
    val apiBuild =
        tasks.register<KotlinApiBuildTask>("apiBuild") {
            dependsOn(unpackApiJar)
            inputJar.set(layout.buildDirectory.file("api/classes/classes.jar"))
            outputApiFile.set(layout.buildDirectory.file("api/PushPortLibrary.api"))
            runtimeClasspath.from(abiValidator)
            ignoredClasses.addAll("dev.pushport.sdk.BuildConfig", "dev.pushport.sdk.R")
        }
    tasks.register<KotlinApiCompareTask>("apiCheck") {
        group = "verification"
        description = "Check the release AAR's Kotlin and JVM API against the reviewed baseline."
        projectApiFile.set(layout.projectDirectory.file("api/PushPortLibrary.api"))
        generatedApiFile.set(apiBuild.flatMap { it.outputApiFile })
    }
    tasks.register<Copy>("apiDump") {
        group = "verification"
        description = "Update the reviewed API baseline; inspect the diff before accepting it."
        from(apiBuild.flatMap { it.outputApiFile })
        into(layout.projectDirectory.dir("api"))
    }
}
dependencies {
    implementation("com.google.firebase:firebase-messaging:25.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    testImplementation("androidx.work:work-testing:2.10.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.tngtech.archunit:archunit:1.4.2")
}

dokka {
    moduleName.set("PushPort Android SDK")
    modulePath.set("android-sdk")
    dokkaPublications.configureEach {
        failOnWarning.set(true)
        includes.from("docs/module.md")
    }
    dokkaSourceSets.configureEach {
        suppress.set(name != "release")
        reportUndocumented.set(true)
        skipEmptyPackages.set(true)
        perPackageOption {
            matchingRegex.set(".*\\.internal(\\..*)?")
            suppress.set(true)
        }
    }
}

val dokkaJar by tasks.registering(Jar::class) {
    group = "documentation"
    description = "Package the generated public Kotlin API reference."
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifact(dokkaJar)
                artifactId = "android-sdk"
                pom {
                    name.set("PushPort Android SDK")
                    description.set("Android push registration and notifications for PushPort")
                    url.set("https://pushport.dev")
                    inceptionYear.set("2026")
                    scm {
                        url.set("https://github.com/IamFromUA/PushPortLibraryAndroid")
                        connection.set("scm:git:https://github.com/IamFromUA/PushPortLibraryAndroid.git")
                        developerConnection.set("scm:git:ssh://git@github.com/IamFromUA/PushPortLibraryAndroid.git")
                    }
                    issueManagement {
                        system.set("GitHub")
                        url.set("https://github.com/IamFromUA/PushPortLibraryAndroid/issues")
                    }
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("IamFromUA")
                            name.set("Oleh Yurkov")
                            email.set("support@pushport.dev")
                            url.set("https://pushport.dev")
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                name = "localRelease"
                url = uri(rootProject.layout.buildDirectory.dir("maven-repository"))
            }
        }
    }
}

val ktlint by configurations.creating { isCanBeConsumed = false }
dependencies { ktlint("com.pinterest.ktlint:ktlint-cli:1.8.0") }

tasks.register<JavaExec>("checkKotlinStyle") {
    group = "verification"
    description = "Check SDK Kotlin source and build script formatting"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = projectDir
    args("src/**/*.kt", "*.gradle.kts")
}

tasks.register<JavaExec>("formatKotlinStyle") {
    group = "formatting"
    description = "Format SDK Kotlin source and build script"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = projectDir
    args("-F", "src/**/*.kt", "*.gradle.kts")
}

tasks.named("check") { dependsOn("checkKotlinStyle", "apiCheck", "dokkaGenerate") }

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<Jar>().configureEach {
    from(sdkLicenseFiles) { into(sdkLicensePath) }
}

mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
