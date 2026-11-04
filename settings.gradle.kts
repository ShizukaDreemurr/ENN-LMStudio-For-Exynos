val jvmOnly = providers.gradleProperty("jvmOnly").orNull == "true"
pluginManagement {
    repositories {
        if (providers.gradleProperty("jvmOnly").orNull != "true") google()
        mavenCentral(); gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { if (!jvmOnly) google(); mavenCentral() }
}
rootProject.name = "ExynosLMStudio"

// Pure-Kotlin (JVM) modules: platform-independent logic, unit-testable without an Android SDK.
include(":core-model", ":core-runtime", ":core-huggingface", ":runtime-agent")

// Android modules. Pass -PjvmOnly=true to build/test only the JVM modules (e.g. on CI without SDK).
if (!jvmOnly) {
    include(":app", ":core-storage", ":core-telemetry", ":runtime-cpu", ":runtime-gpu", ":runtime-enn")
}
