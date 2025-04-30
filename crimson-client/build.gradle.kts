import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("com.android.library") version "8.9.2"
}

group = "com.milkcocoa.info.crimson"
version = "unspecified"

kotlin {
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
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = JvmTarget.JVM_17.target
        }
    }
    js(IR){
        browser()
        nodejs()
    }
//    iosX64()
//    iosArm64()
//    iosSimulatorArm64()

    sourceSets{
        commonMain.dependencies {
            api(libs.ktor.client.core)
            api(libs.ktor.client.websockets)
            api(libs.kotlinx.coroutines)

            api(project(":crimson-core"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.websockets)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.okhttp)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.okhttp)
        }

        jsMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.js)
        }

//        iosMain.dependencies {
//            implementation("io.ktor:ktor-client-darwin:3.1.2")
//        }
    }
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