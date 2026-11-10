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
    buildFeatures { buildConfig = true }
    defaultConfig {
        // Feature flag from gradle.properties / -Penn.enabled=true
        buildConfigField("boolean", "ENN_ENABLED", (project.findProperty("enn.enabled") ?: "false").toString())
    }
    // Self-built libLiteRt.so + libLiteRtDispatch_Samsung.so (from LiteRT tag v2.3.2; the Maven libLiteRt.so hides abseil
    // exports the dispatch needs). Put them in runtime-enn/jniLibs/arm64-v8a/. Do NOT add libenn_public_api_cpp.so (system lib).
    sourceSets["main"].jniLibs.srcDir("jniLibs")

}
dependencies {
    api(project(":core-runtime"))
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    // LiteRT 2.3.2 (same as the reference app). The flag only decides whether LiteRtNpuBridge is used at runtime.
    implementation(libs.litert)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
