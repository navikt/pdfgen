package no.nav.pdfgen.plugins

import com.github.jknack.handlebars.Template
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import no.nav.pdfgen.Environment
import no.nav.pdfgen.logger

fun Application.configureStatusPages(
    templates: Map<Pair<String, String>, Template>,
    environment: Environment,
) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Caught exception", cause)
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                TextContent(
                    messageFor404(templates, call.request.path(), environment),
                    ContentType.Text.Plain.withCharset(Charsets.UTF_8),
                    HttpStatusCode.NotFound,
                ),
            )
        }
    }
}

private fun messageFor404(
    templates: Map<Pair<String, String>, Template>,
    path: String,
    environment: Environment,
): String = if (environment.enableHtmlEndpoint && !environment.disablePdfGet) {
    "Unkown path '$path'. Known templates:\n" +
        templates
            .map { (app, _) -> "/api/v1/genpdf/%s/%s".format(app.first, app.second) }
            .joinToString("\n") +
        templates
            .map { (app, _) -> "/api/v1/genhtml/%s/%s".format(app.first, app.second) }
            .joinToString("\n")
} else {
    "Unkown path '$path'. Known templates:\n" +
        templates
            .map { (app, _) -> "/api/v1/genpdf/%s/%s".format(app.first, app.second) }
            .joinToString("\n")
}
