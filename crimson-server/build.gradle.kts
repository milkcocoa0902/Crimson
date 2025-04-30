plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.20"
}

group = "com.milkcocoa.info.crimson"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":crimson-core"))
    api(libs.ktor.server.core)
    api(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    api(libs.lettuce.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}