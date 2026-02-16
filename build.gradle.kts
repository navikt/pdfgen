import com.diffplug.gradle.spotless.SpotlessTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.pdfgen"
version = "2.0.0" //This will never change. See GitHub releases for docker image release

val javaVersion = JvmTarget.JVM_25


val handlebarsVersion = "4.3.1"
val jacksonVersion = "2.21.0"
val ktorVersion = "3.4.0"
val logbackVersion = "1.5.28"
val logstashEncoderVersion = "9.0"
val openHtmlToPdfVersion = "1.1.37"
val prometheusVersion = "0.16.0"
val junitJupiterVersion = "6.0.2"
val verapdfVersion = "1.28.2"
val ktfmtVersion = "0.44"
val testcontainersVersion = "2.0.3"
val pdfgencoreVersion = "1.1.72"

///Due to vulnerabilities
val commonsCompressVersion = "1.28.0"
val commonsIoVersion = "2.21.0"
val rhinoVersion = "1.9.0"

plugins {
    id("application")
    kotlin("jvm") version "2.3.0"
    id("com.diffplug.spotless") version "8.2.1"
    id("com.github.ben-manes.versions") version "0.53.0"
}

application {
    mainClass.set("no.nav.pdfgen.ApplicationKt")
}

kotlin {
    compilerOptions {
        jvmTarget.set(javaVersion)
    }
}

tasks {

    withType<Test> {
        useJUnitPlatform {}
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

   withType<Exec> {
        println("⚈ ⚈ ⚈ Running Add Pre Commit Git Hook Script on Build ⚈ ⚈ ⚈")
        commandLine("cp", "./.scripts/pre-commit", "./.git/hooks")
        println("✅ Added Pre Commit Git Hook Script.")

    }

    withType<SpotlessTask> {
        spotless{
            kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
            check {
                dependsOn("spotlessApply")
            }
        }
    }
    
}

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("no.nav.pdfgen:pdfgen-core:$pdfgencoreVersion")

    implementation("com.github.jknack:handlebars:$handlebarsVersion")
    implementation("com.github.jknack:handlebars-jackson2:$handlebarsVersion")
    implementation("io.github.openhtmltopdf:openhtmltopdf-pdfbox:$openHtmlToPdfVersion")
    implementation("io.github.openhtmltopdf:openhtmltopdf-slf4j:$openHtmlToPdfVersion")
    implementation("io.github.openhtmltopdf:openhtmltopdf-svg-support:$openHtmlToPdfVersion")
    constraints {
        implementation("commons-io:commons-io:$commonsIoVersion") {
            because("Due to vulnerabilities in io.github.openhtmltopdf:openhtmltopdf-svg-support")
        }
    }

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")

    implementation("org.verapdf:validation-model:$verapdfVersion")
    constraints {
        implementation("org.mozilla:rhino:$rhinoVersion") {
            because("Due to vulnerabilities in org.verapdf:validation-model")
        }
    }

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    constraints {
        implementation("org.apache.commons:commons-compress:$commonsCompressVersion") {
            because("Due to vulnerabilities, see CVE-2024-26308")
        }
    }
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")

}
