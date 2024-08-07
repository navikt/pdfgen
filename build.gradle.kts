import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.pdfgen"
version = "2.0.0" //This will never change. See GitHub releases for docker image release

val handlebarsVersion = "4.3.1"
val jacksonVersion = "2.17.2"
val jaxbVersion = "4.0.4"
val jaxbApiVersion = "2.3.1"
val jsoupVersion = "1.16.2"
val kluentVersion = "1.72"
val ktorVersion = "2.3.12"
val logbackVersion = "1.5.6"
val logstashEncoderVersion = "8.0"
val openHtmlToPdfVersion = "pdfbox2-65c2c5010f84b2daa5821971c9c68cd330463830"
val prometheusVersion = "0.16.0"
val junitJupiterVersion = "5.10.3"
val verapdfVersion = "1.26.1"
val ktfmtVersion = "0.44"
val testcontainersVersion= "1.20.1"
val pdfgencoreVersion = "1.1.22"
val commonsCompressVersion = "1.26.2"
val javaVersion = JvmTarget.JVM_21


plugins {
    id("application")
    kotlin("jvm") version "2.0.10"
    id("com.diffplug.spotless") version "6.25.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.github.ben-manes.versions") version "0.51.0"
}

application {
    mainClass.set("no.nav.pdfgen.BootstrapKt")
}

kotlin {
    compilerOptions {
        jvmTarget.set(javaVersion)
    }
}

tasks {

    test {
        useJUnitPlatform {}
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }

    shadowJar {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        isZip64 = true
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "no.nav.pdfgen.BootstrapKt",
                ),
            )
        }
    }

    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
        check {
            dependsOn("spotlessApply")
        }
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
    maven {
        url = uri("https://maven.pkg.github.com/openhtmltopdf/openhtmltopdf")
        credentials {
            username = "token"
            password = System.getenv("ORG_GRADLE_PROJECT_githubPassword")
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("no.nav.pdfgen:pdfgen-core:$pdfgencoreVersion")

    implementation("com.github.jknack:handlebars:$handlebarsVersion")
    implementation("com.github.jknack:handlebars-jackson2:$handlebarsVersion")
    implementation("at.datenwort.openhtmltopdf:openhtmltopdf-pdfbox:$openHtmlToPdfVersion")
    implementation("at.datenwort.openhtmltopdf:openhtmltopdf-slf4j:$openHtmlToPdfVersion")
    implementation("at.datenwort.openhtmltopdf:openhtmltopdf-svg-support:$openHtmlToPdfVersion")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")

    implementation("org.verapdf:validation-model:$verapdfVersion")

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
}
