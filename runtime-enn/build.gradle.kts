plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "dev.exynoslm.runtime.enn"
    compileSdk = 35
    defaultConfig { minSdk = 31; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    defaultConfig {
        // Feature flag from gradle.properties / -Penn.enabled=true
        buildConfigField("boolean", "ENN_ENABLED", (project.findProperty("enn.enabled") ?: "false").toString())
    }
}
dependencies {
    api(project(":core-runtime"))
    implementation(libs.coroutines.android)
    // Real Samsung ENN SDK artifact goes here once you have legitimate access, e.g.:
    // if (project.findProperty("enn.enabled") == "true") implementation(files("libs/<vendor-enn-sdk>.aar"))
}
