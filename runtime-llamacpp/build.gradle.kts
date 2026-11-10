plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "dev.exynoslm.runtime.llamacpp"
    compileSdk = 35
    // The Ubuntu 24.04 runner image used by ubuntu-latest preinstalls NDK 27.3.13750724 (default), 29.0.14206865 and
    // 30.0.16248370 (actions/runner-images Ubuntu2404-Readme.md, checked when this module was written).
    ndkVersion = "27.3.13750724"
    defaultConfig {
        minSdk = 31
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            // The runner image has CMake 3.31.5 and 4.1.2 in the SDK; AGP's default 3.22.1 is not there and would be downloaded.
            version = "3.31.5"
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    api(project(":core-runtime"))
    // CpuTuning (big-core thread budget) and PromptFormatter live in runtime-cpu.
    implementation(project(":runtime-cpu"))
    implementation(libs.coroutines.android)
}
