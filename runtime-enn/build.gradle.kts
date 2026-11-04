plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
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
    // Self-built libLiteRt.so + libLiteRtDispatch_Samsung.so (from LiteRT tag v2.3.2; the Maven libLiteRt.so hides abseil
    // exports the dispatch needs). Put them in runtime-enn/jniLibs/arm64-v8a/. Do NOT add libenn_public_api_cpp.so (system lib).
    sourceSets["main"].jniLibs.srcDir("jniLibs")
    packaging { jniLibs { pickFirsts += "lib/arm64-v8a/libLiteRt.so" } }
}
dependencies {
    api(project(":core-runtime"))
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    if (project.findProperty("enn.enabled") == "true") {
        // LiteRT 2.3.2 CompiledModel API (reached via reflection in LiteRtNpuBridge).
        // NOTE: this upgrades LiteRT app-wide; re-check runtime-gpu's CompatibilityList usage against 2.x.
        implementation(libs.litert.npu)
    }
}
