plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "dev.exynoslm.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.exynoslm.app"
        minSdk = 31
        targetSdk = 35
        // CI sets -PbuildNumber=<run number> so every APK has a higher versionCode and installs over the previous one.
        val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/exynoslm-shared.jks")
            storePassword = "exynoslm"; keyAlias = "exynoslm"; keyPassword = "exynoslm"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("shared") }
        release {
            signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions {
        jvmTarget = "17"
        // LiteRT 2.3.2 ships Kotlin 2.4 metadata; our compiler (2.0.21) only reads up to 2.1.
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
    buildFeatures { compose = true; buildConfig = true }
    androidResources { generateLocaleConfig = true }
    packaging {
        jniLibs {
            // Must be extracted: Environment.Option.DispatchLibraryDir points at applicationInfo.nativeLibraryDir.
            useLegacyPackaging = true
            // Self-built libLiteRt.so (abseil exports) vs. the one in the Maven AAR. Which copy wins is UNVERIFIED:
            // check with `unzip -l app-debug.apk | grep libLiteRt` and compare sizes with runtime-enn/jniLibs.
            pickFirsts += "**/libLiteRt.so"
        }
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-runtime"))
    implementation(project(":core-huggingface"))
    implementation(project(":core-storage"))
    implementation(project(":core-telemetry"))
    implementation(project(":runtime-cpu"))
    implementation(project(":runtime-gpu"))
    implementation(project(":runtime-enn"))
    implementation(project(":runtime-litertlm"))
    implementation(project(":runtime-llamacpp"))
    implementation(project(":runtime-agent"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
