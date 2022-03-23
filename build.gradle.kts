import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.pdfgen"
version = "1.4.6"

val handlebarsVersion = "4.3.0"
val jacksonVersion = "2.13.2"
val jaxbVersion = "3.0.2"
val jaxbApiVersion = "2.3.1"
val jsoupVersion = "1.14.3"
val kluentVersion = "1.68"
val ktorVersion = "1.6.8"
val logbackVersion = "1.2.11"
val logstashEncoderVersion = "7.0.1"
val openHtmlToPdfVersion = "1.0.10"
val prometheusVersion = "0.15.0"
val spekVersion = "2.0.17"
val verapdfVersion = "1.20.1"

plugins {
    kotlin("jvm") version "1.6.10"
    id("org.jmailen.kotlinter") version "3.9.0"
    id("com.github.johnrengelman.shadow") version "6.0.0"
    id("com.github.ben-manes.versions") version "0.42.0"
}

tasks {
    create("printVersion") {
        doLast {
            println(project.version)
        }
    }
    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    withType<Jar> {
        manifest.attributes("Main-Class" to "no.nav.pdfgen.BootstrapKt")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    implementation("com.github.jknack:handlebars:$handlebarsVersion")
    implementation("com.github.jknack:handlebars-jackson2:$handlebarsVersion")
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:$openHtmlToPdfVersion")
    implementation("com.openhtmltopdf:openhtmltopdf-slf4j:$openHtmlToPdfVersion")
    implementation("com.openhtmltopdf:openhtmltopdf-svg-support:$openHtmlToPdfVersion")

    implementation("org.jsoup:jsoup:$jsoupVersion")
    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")

    implementation("org.verapdf:validation-model:$verapdfVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runtime-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")

    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
}
