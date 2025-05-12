plugins {
    kotlin("jvm") version "2.0.10"
}

group = "funn.j2k"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.0")

    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-network:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    implementation(kotlin("reflect"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}