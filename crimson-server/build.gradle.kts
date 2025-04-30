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
    api("io.ktor:ktor-server-core:3.1.2")
    api("io.ktor:ktor-server-cio:3.1.2")
    implementation("io.ktor:ktor-server-websockets:3.1.2")
    api("io.lettuce:lettuce-core:6.5.5.RELEASE")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}