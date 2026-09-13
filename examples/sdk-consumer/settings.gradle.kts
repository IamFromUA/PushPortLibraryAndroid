pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven {
                    name = "sdkUnderTest"
                    url = uri(rootDir.resolve("../../build/maven-repository"))
                }
            }
            filter { includeGroup("dev.pushport") }
        }
    }
}

rootProject.name = "PushPortConsumerCheck"
