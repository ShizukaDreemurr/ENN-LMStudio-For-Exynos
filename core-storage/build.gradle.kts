plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "dev.exynoslm.storage"
    compileSdk = 35
    defaultConfig { minSdk = 31; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }

}
dependencies {
    api(project(":core-model"))
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
}
