pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "Aphrodite"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// Core
include(":core:common")
include(":core:designsystem")
include(":core:adaptive")
include(":core:database")
include(":core:datastore")
include(":core:security")
include(":core:network")
include(":core:llm")
include(":core:markdown")
include(":core:media")
include(":core:speech")
include(":core:notifications")

// Features
include(":feature:auth")
include(":feature:chat")
include(":feature:compare")
include(":feature:research")
include(":feature:notes")
include(":feature:tasks")
include(":feature:calendar")
include(":feature:contacts")
include(":feature:email")
include(":feature:documents")
include(":feature:gallery")
include(":feature:memory")
include(":feature:skills")
include(":feature:search")
include(":feature:cookbook")
include(":feature:tools")
include(":feature:settings")
include(":feature:admin")

// On-device inference
include(":inference:llama")
