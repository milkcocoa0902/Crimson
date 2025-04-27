plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
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
        compilations.all {
            kotlinOptions.jvmTarget = "17"
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
            api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

            api("io.ktor:ktor-client-core:3.1.2")
            api("io.ktor:ktor-client-websockets:3.1.2")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

            api(project(":crimson-core"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-server-core:3.1.2")
            implementation("io.ktor:ktor-server-cio:3.1.2")
            implementation("io.ktor:ktor-server-websockets:3.1.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
        }

        androidMain.dependencies {
            implementation("io.ktor:ktor-client-cio:3.1.2")
            implementation("io.ktor:ktor-client-okhttp:3.1.2")
        }

        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-cio:3.1.2")
            implementation("io.ktor:ktor-client-okhttp:3.1.2")
        }

        jsMain.dependencies {
            implementation("io.ktor:ktor-client-cio:3.1.2")
            implementation("io.ktor:ktor-client-js:3.1.2")
        }

//        iosMain.dependencies {
//            implementation("io.ktor:ktor-client-darwin:3.1.2")
//        }
    }
}

android {
    namespace = "com.milkcocoa.info.crimson"
    compileSdk = 34

}