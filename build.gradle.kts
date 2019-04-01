import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.pdfgen"
version = "1.1.7-SNAPSHOT"

val handlebarsVersion = "4.1.2"
val jacksonVersion = "2.9.8"
val jaxbVersion = "2.3.1"
val jsoupVersion = "1.11.3"
val kluentVersion = "1.49"
val ktorVersion = "1.1.3"
val kotlinVersion = "1.3.10"
val logbackVersion = "1.2.3"
val logstashEncoderVersion = "5.2"
val openHtmlToPdfVersion = "0.0.1-RC17"
val prometheusVersion = "0.6.0"
val spekVersion = "2.0.1"
val verapdfVersion = "1.12.1"

plugins {
    kotlin("jvm") version "1.3.21"
    id("org.jmailen.kotlinter") version "1.22.0"
    id("com.github.johnrengelman.shadow") version "5.0.0"
    id("com.github.ben-manes.versions") version "0.21.0"
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
        kotlinOptions.jvmTarget = "1.8"
    }
    withType<Jar> {
        manifest.attributes("Main-Class" to "no.nav.pdfgen.BootstrapKt")
    }
}

repositories {
    mavenCentral()
    jcenter()
    maven(url="https://dl.bintray.com/kotlin/ktor")
    maven(url= "https://dl.bintray.com/spekframework/spek-dev")
}

dependencies {
    compile(kotlin("stdlib"))

    implementation("com.github.jknack:handlebars:$handlebarsVersion")
    implementation("com.github.jknack:handlebars-jackson2:$handlebarsVersion")
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:$openHtmlToPdfVersion")
    implementation("com.openhtmltopdf:openhtmltopdf-slf4j:$openHtmlToPdfVersion")
    implementation("com.openhtmltopdf:openhtmltopdf-svg-support:$openHtmlToPdfVersion")

    implementation("org.jsoup:jsoup:$jsoupVersion")
    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("javax.xml.bind:jaxb-api:2.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runtime-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")

    testImplementation("org.verapdf:validation-model:$verapdfVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
}
