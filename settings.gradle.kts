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
    }
}

rootProject.name = "Digibuddy"

include(
    ":apps:customer",
    ":apps:customer:androidApp",
    ":apps:helpers",
    ":apps:helpers:androidApp",
    ":backend",
    ":shared:contracts",
    ":shared:core",
    ":shared:authentication",
    ":shared:database",
    ":shared:designsystem",
    ":shared:networking",
    ":shared:profile",
    ":shared:helper-dashboard",
    ":shared:helper-onboarding",
)
