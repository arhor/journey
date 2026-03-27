@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "journey"

include(
    ":app",
    ":data",
    ":domain",
    ":core:common",
    ":core:navigation",
    ":core:ui",
    ":feature:exploration",
    ":feature:hero",
    ":feature:map",
    ":feature:map:fog-of-war",
    ":feature:settings",
)
