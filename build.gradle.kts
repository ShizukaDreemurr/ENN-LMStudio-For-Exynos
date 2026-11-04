// AGP is put on the root classpath only for full (Android) builds, so `-PjvmOnly=true`
// works on machines/CI without Google Maven or an Android SDK.
buildscript {
    val jvmOnly = gradle.startParameter.projectProperties["jvmOnly"] == "true"
    repositories { if (!jvmOnly) google(); mavenCentral() }
    dependencies {
        if (!jvmOnly) {
            classpath("com.android.tools.build:gradle:${libs.versions.agp.get()}")
            classpath("com.google.dagger:hilt-android-gradle-plugin:${libs.versions.hilt.get()}")
        }
    }
}
plugins {
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// LiteRT 2.3.2 ships Kotlin 2.4 metadata; Kotlin 2.0.21 can read up to 2.1. Applies to every module touching LiteRT.
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}
