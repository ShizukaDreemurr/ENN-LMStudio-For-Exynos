// AGP is put on the root classpath only for full (Android) builds, so `-PjvmOnly=true`
// works on machines/CI without Google Maven or an Android SDK.
buildscript {
    val jvmOnly = gradle.startParameter.projectProperties["jvmOnly"] == "true"
    repositories { if (!jvmOnly) google(); mavenCentral() }
    dependencies {
        if (!jvmOnly) classpath("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    }
}
plugins {
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
