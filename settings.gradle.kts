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
        // projectM dla Androida (AAR z prekompilowanym libprojectM.so, Prefab) — zwendorowane
        // lokalnie zamiast pobierane z serwera protyposis.github.io przy każdym buildzie, bo to
        // małe, jednoosobowe repo ("experimental... not guaranteed to stay available forever" —
        // jego własny README). Patrz DESIGN.md, Etap 9, i local-maven-repo/README.md.
        maven { url = uri("$rootDir/local-maven-repo") }
    }
}

rootProject.name = "Aurora"

include(":app")
include(":core:designsystem")
include(":core:projectm")
include(":domain")
include(":data")
include(":desktop")
include(":core:sync")
