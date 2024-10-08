package no.nav.pdfgen.plugins

import com.github.jknack.handlebars.Template
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.nav.pdfgen.ApplicationState
import no.nav.pdfgen.logger

fun Application.configureStatusPages(
    templates: Map<Pair<String, String>, Template>,
    applicationState: ApplicationState
) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            logger.error("Caught exception", cause)
            applicationState.alive = false
            applicationState.ready = false
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                TextContent(
                    messageFor404(templates, call.request.path()),
                    ContentType.Text.Plain.withCharset(Charsets.UTF_8),
                    HttpStatusCode.NotFound,
                ),
            )
        }
    }
}

private fun messageFor404(templates: Map<Pair<String, String>, Template>, path: String) =
    "Unkown path '$path'. Known templates:\n" +
        templates
            .map { (app, _) -> "/api/v1/genpdf/%s/%s".format(app.first, app.second) }
            .joinToString("\n")
