package no.nav.pdfgen.plugins

import com.github.jknack.handlebars.Template
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import no.nav.pdfgen.Environment
import no.nav.pdfgen.logger

fun Application.configureStatusPages(
    templates: Map<Pair<String, String>, Template>,
    environment: Environment,
) {
    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respondError(HttpStatusCode.BadRequest, "Bad Request")
        }
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respondError(HttpStatusCode.UnsupportedMediaType, "Unsupported Media Type")
        }
        exception<CannotTransformContentToTypeException> { call, _ ->
            call.respondError(HttpStatusCode.UnsupportedMediaType, "Unsupported Media Type")
        }
        exception<NotFoundException> { call, _ ->
            call.respondNotFound(templates, environment)
        }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) {
                throw cause
            }
            logger.error("Caught exception", cause)
            call.respondError(HttpStatusCode.InternalServerError, "Internal Server Error")
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondNotFound(templates, environment)
        }
    }
}

private suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    message: String,
) {
    respond(
        TextContent(
            message,
            ContentType.Text.Plain.withCharset(Charsets.UTF_8),
            status,
        ),
    )
}

private suspend fun ApplicationCall.respondNotFound(
    templates: Map<Pair<String, String>, Template>,
    environment: Environment,
) {
    respondError(
        HttpStatusCode.NotFound,
        messageFor404(templates, request.path(), environment),
    )
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
