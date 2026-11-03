import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.pdfgen"
version = "2.0.0" // This will never change. See GitHub releases for docker image release

val javaVersion = 25

val handlebarsVersion = "4.5.8"
val handlebarsJackson2Version = "4.3.1"
val jacksonVersion = "2.22.4"
val jackson3Version = "3.2.4"
val ktorVersion = "3.5.2"
val logbackVersion = "1.6.6"
val logstashEncoderVersion = "9.0"
val openHtmlToPdfVersion = "1.1.100"
val prometheusVersion = "0.16.0"
val junitJupiterVersion = "6.1.3"
val verapdfVersion = "1.30.3"
val testcontainersVersion = "2.0.5"
val pdfgencoreVersion = "1.1.84"
val ktLintClientVersion = "1.8.0"

// Transitive dependency alignment
val commonsCompressVersion = "1.28.0"
val commonsIoVersion = "2.22.0"
val rhinoVersion = "1.9.1"
val nettyVersion = "4.2.19.Final"

plugins {
    id("application")
    kotlin("jvm") version "2.4.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("com.github.ben-manes.versions") version "0.66.0"
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
    constraints {
        implementation("io.github.openhtmltopdf:openhtmltopdf-svg-support:$openHtmlToPdfVersion") {
            because("Keep SVG support aligned with the PDF renderer")
        }
        implementation("commons-io:commons-io:$commonsIoVersion") {
            because("Due to vulnerabilities in io.github.openhtmltopdf:openhtmltopdf-svg-support")
        }
        implementation("org.mozilla:rhino:$rhinoVersion") {
            because("Due to vulnerabilities in org.verapdf:validation-model-jakarta")
        }
        implementation("org.apache.commons:commons-compress:$commonsCompressVersion") {
            because("Due to vulnerabilities, see CVE-2024-26308")
        }
    }

    // PDF generation and templates
    implementation("no.nav.pdfgen:pdfgen-core:$pdfgencoreVersion")
    implementation("com.github.jknack:handlebars:$handlebarsVersion")
    implementation("com.github.jknack:handlebars-jackson2:$handlebarsJackson2Version")
    implementation("io.github.openhtmltopdf:openhtmltopdf-pdfbox:$openHtmlToPdfVersion")
    implementation("io.github.openhtmltopdf:openhtmltopdf-slf4j:$openHtmlToPdfVersion")
    implementation("org.verapdf:validation-model-jakarta:$verapdfVersion")

    // HTTP server
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-status-pages")

    // JSON serialization
    implementation("io.ktor:ktor-serialization-jackson")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // Logging and metrics
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.ktor:ktor-client-cio")
    testImplementation("io.ktor:ktor-client-content-negotiation")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
}
