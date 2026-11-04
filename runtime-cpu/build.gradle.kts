plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "dev.exynoslm.runtime.cpu"
    compileSdk = 35
    defaultConfig { minSdk = 31; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }

}
dependencies {
    api(project(":core-runtime"))
    implementation(libs.coroutines.android)
    // Baseline LLM engine (CPU + GPU backends). TODO: migrate to LiteRT-LM (.litertlm) once its Android API is pinned; keep InferenceEngine unchanged.
    api(libs.mediapipe.genai)
}
