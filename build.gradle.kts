import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.pdfgen"
version = "2.0.0" // This will never change. See GitHub releases for docker image release

val javaVersion = 25

val pdfgencoreVersion = "1.1.91"
val handlebarsJackson2Version = "4.3.1"
val jackson3Version = "3.2.4"
val ktorVersion = "3.6.1"
val logbackVersion = "1.6.6"
val logstashEncoderVersion = "9.0"
val openHtmlToPdfVersion = "1.1.105"
val micrometerVersion = "1.17.2"
val junitJupiterVersion = "6.1.3"
val verapdfVersion = "1.30.3"
val testcontainersVersion = "2.0.5"
val ktLintClientVersion = "1.8.0"

// Transitive dependency alignment
val commonsCompressVersion = "1.28.0"
val nettyVersion = "4.2.19.Final"

plugins {
    id("application")
    kotlin("jvm") version "2.4.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.github.ben-manes.versions") version "0.66.0"
}

application { mainClass = "no.nav.pdfgen.ApplicationKt" }

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaVersion.toString())
    }
}

ktlint { version = ktLintClientVersion }

tasks.named<Test>("test") {
    // DockerImageTest requires the application distribution.
    dependsOn(tasks.named("installDist"))
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    // Dependency alignment
    implementation(platform("io.ktor:ktor-bom:$ktorVersion"))
    implementation(platform("io.netty:netty-bom:$nettyVersion"))
    implementation(platform("tools.jackson:jackson-bom:$jackson3Version"))

    // PDF generation and templates
    implementation("no.nav.pdfgen:pdfgen-core:$pdfgencoreVersion")
    implementation("io.github.openhtmltopdf:openhtmltopdf-slf4j:$openHtmlToPdfVersion")
    implementation("org.verapdf:validation-model-jakarta:$verapdfVersion")

    // HTTP server
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-status-pages")

    // JSON serialization
    implementation("io.ktor:ktor-serialization-jackson")

    // Logging and metrics
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")

    // Testing
    testImplementation("io.ktor:ktor-client-cio")
    testImplementation("io.ktor:ktor-client-content-negotiation")
    testImplementation("io.ktor:ktor-server-test-host")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")

    testImplementation("io.github.openhtmltopdf:openhtmltopdf-pdfbox:$openHtmlToPdfVersion")
    testImplementation("com.github.jknack:handlebars-jackson2:$handlebarsJackson2Version")
}
