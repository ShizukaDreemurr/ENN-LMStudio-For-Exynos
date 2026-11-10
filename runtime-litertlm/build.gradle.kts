plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "dev.exynoslm.runtime.litertlm"
    compileSdk = 35
    defaultConfig { minSdk = 31; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    api(project(":core-runtime"))
    implementation(libs.coroutines.android)
    // LiteRT-LM Kotlin API (.litertlm models). Carries Kotlin 2.4 metadata; root build.gradle.kts adds -Xskip-metadata-version-check.
    api(libs.litertlm.android)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
