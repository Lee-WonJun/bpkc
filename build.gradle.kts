plugins {
    kotlin("jvm") version "2.0.10"
}

group = "org.seeroe"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")

}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(19)
}