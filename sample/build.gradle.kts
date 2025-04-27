plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.20"
}

group = "com.milkcocoa.info.crimson"
version = "unspecified"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation(project(":annotation"))
    implementation(project(":crimson-client"))
    implementation(project(":crimson-server"))
    implementation("io.ktor:ktor-server-core:3.1.2")
    implementation("io.ktor:ktor-server-websockets:3.1.2")
    implementation("io.ktor:ktor-server-core:3.1.2")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("io.ktor:ktor-client-cio:3.1.2")
    implementation("io.ktor:ktor-client-websockets:3.1.2")
    implementation("io.ktor:ktor-client-okhttp:3.1.2")
}

tasks.test {
    useJUnitPlatform()
}