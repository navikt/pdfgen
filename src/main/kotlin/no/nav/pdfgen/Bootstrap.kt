package no.nav.pdfgen

// Uncommemt to enable debug to file
// import java.io.File

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.jknack.handlebars.Template
import com.openhtmltopdf.slf4j.Slf4jLogger
import com.openhtmltopdf.util.XRLog
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.pdfgen.api.setupGeneratePdfApi
import no.nav.pdfgen.template.loadTemplates
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.verapdf.pdfa.VeraGreenfieldFoundryProvider

val objectMapper: ObjectMapper = ObjectMapper()
    .registerKotlinModule()

val log: Logger = LoggerFactory.getLogger("pdfgen")

fun main() {
    initializeApplication(8080).start(wait = true)
}

fun initializeApplication(port: Int): ApplicationEngine {
    System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider")
    VeraGreenfieldFoundryProvider.initialise()

    val env = Environment()
    val templates = loadTemplates(env)
    val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    XRLog.setLoggerImpl(Slf4jLogger())

    return embeddedServer(
        Netty, port,
        configure = {
            responseWriteTimeoutSeconds = 60 // Increase timeout of Netty to handle large content bodies
        }
    ) {
        install(ContentNegotiation) {
            jackson {}
        }

        install(StatusPages) {
            status(HttpStatusCode.NotFound) { call, _ ->
                call.respond(
                    TextContent(
                        messageFor404(templates, Routing(this@embeddedServer), call.request.path()),
                        ContentType.Text.Plain.withCharset(Charsets.UTF_8),
                        HttpStatusCode.NotFound
                    )
                )
            }
        }
        routing {
            get("/internal/is_ready") {
                call.respondText("I'm ready")
            }
            get("/internal/is_alive") {
                call.respondText("I'm alive")
            }
            get("/internal/prometheus") {
                val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: setOf()
                call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching {
                            TextFormat.write004(
                                this@respondTextWriter,
                                collectorRegistry.filteredMetricFamilySamples(names)
                            )
                        }
                    }
                }
            }
            setupGeneratePdfApi(env, templates)
        }
    }
}

private fun messageFor404(templates: Map<Pair<String, String>, Template>, routing: Routing, path: String) =
    "Unkown path '$path'. Known paths:" + templates.map { (app, _) ->
        val (applicationName, template) = app
        apiRoutes(routing)
            .map { it.replace("{applicationName}", applicationName) }.joinToString("\n") { it.replace("{template}", template) }
    }.joinToString("\n")

private fun apiRoutes(routing: Routing) = allRoutes(routing).filter {
    it.selector is HttpMethodRouteSelector && it.toString().startsWith("/api")
}.map { it.toString() }

private fun allRoutes(route: Route): List<Route> = listOf(route) + route.children.flatMap(::allRoutes)
