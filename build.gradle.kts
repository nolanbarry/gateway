plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "com.nolanbarry"
version = "0.2.4-beta"

repositories {
    mavenCentral()
}

application {
    mainClass = "com.nolanbarry.gateway.GatewayKt"
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(kotlin("reflect"))
    implementation("io.ktor:ktor-server-core:2.3.5")
    implementation("io.ktor:ktor-network:2.3.5")
    implementation("io.ktor:ktor-network-tls:2.3.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.23.1")
    implementation("org.apache.logging.log4j:log4j-core:2.23.1")

    implementation("aws.sdk.kotlin:ec2-jvm:1.0.69")
}

kotlin {
    jvmToolchain(19)
}

tasks {
    test {
        useJUnitPlatform()
    }
}