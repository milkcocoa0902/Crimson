import org.gradle.kotlin.dsl.api
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform") version "2.1.20"
    id("com.android.library") version "8.9.0"
}

group = "com.milkcocoa.info.crimson"
version = "unspecified"

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        publishLibraryVariants(
            "release",
//            "debug"
        )
    }
    js(IR)
    sourceSets{
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            api("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.8.1")
            api("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.8.1")
        }
    }
    jvmToolchain(17)
}

android {
    namespace = "com.milkcocoa.info.crimson"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
}