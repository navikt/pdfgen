package no.nav.pdfgen.plugins

import com.github.jknack.handlebars.Template
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
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
    environment: Environment
): String {
    if (environment.enableHtmlEndpoint && !environment.disablePdfGet) {
        return "Unkown path '$path'. Known templates:\n" +
            templates
                .map { (app, _) -> "/api/v1/genpdf/%s/%s".format(app.first, app.second) }
                .joinToString("\n") +
            templates
                .map { (app, _) -> "/api/v1/genhtml/%s/%s".format(app.first, app.second) }
                .joinToString("\n")
    } else {

        return "Unkown path '$path'. Known templates:\n" +
            templates
                .map { (app, _) -> "/api/v1/genpdf/%s/%s".format(app.first, app.second) }
                .joinToString("\n")

    }
}
