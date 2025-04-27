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
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    js(IR)
    sourceSets{
        commonMain.dependencies {

        }
    }
    jvmToolchain(17)
}

android {
    namespace = "com.milkcocoa.info.crimson"
    compileSdk = 34

}