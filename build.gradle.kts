plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    application
}

group = "com.nolanbarry"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

configurations {
    create("aws")
    create("local")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(kotlin("reflect"))
    implementation("io.ktor:ktor-server-core:2.3.4")
    implementation("io.ktor:ktor-network:2.3.4")
    implementation("io.ktor:ktor-network-tls:2.3.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.slf4j:slf4j-simple:2.0.3")

    add("aws", "aws.sdk.kotlin:ec2:0.32.5-beta")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(19)
}

application {
    mainClass.set("com.nolanbarry.gateway.GatewayKt")
}

sourceSets {
    create("aws") {
        compileClasspath += configurations["aws"]
        runtimeClasspath += configurations["aws"]
    }

    create("local") {
        compileClasspath += configurations["local"]
        runtimeClasspath += configurations["local"]
    }
}

tasks.register<Jar>("awsJar") {
    from(sourceSets["aws"].output)
    archiveBaseName.set("${project.name}-aws")
}

tasks.register<Jar>("localJar") {
    from(sourceSets["local"].output)
    archiveBaseName.set("${project.name}-local")
}
