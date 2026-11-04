plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "dev.exynoslm.runtime.gpu"
    compileSdk = 35
    defaultConfig { minSdk = 31; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }

}
dependencies {
    api(project(":runtime-cpu"))
    // LiteRT 2.x GPU accelerator for non-LLM graphs (vision/embedding .tflite).
    implementation(libs.litert)
    implementation(libs.litert.gpu)
}
