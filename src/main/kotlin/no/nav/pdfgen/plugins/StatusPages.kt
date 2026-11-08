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
import no.nav.pdfgen.application.api.ApiPaths
import no.nav.pdfgen.logger

private const val UNSUPPORTED_MEDIA_TYPE_MESSAGE = "Unsupported Media Type"
private const val UNKNOWN_PATH_MESSAGE_TEMPLATE = "Unkown path '%s'. Known templates:\n"

fun Application.configureStatusPages(
    templates: Map<Pair<String, String>, Template>,
    environment: Environment,
) {
    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respondError(HttpStatusCode.BadRequest, HttpStatusCode.BadRequest.description)
        }
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respondError(HttpStatusCode.UnsupportedMediaType, UNSUPPORTED_MEDIA_TYPE_MESSAGE)
        }
        exception<CannotTransformContentToTypeException> { call, _ ->
            call.respondError(HttpStatusCode.UnsupportedMediaType, UNSUPPORTED_MEDIA_TYPE_MESSAGE)
        }
        exception<NotFoundException> { call, _ ->
            call.respondNotFound(templates, environment)
        }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) {
                throw cause
            }
            logger.error("Caught exception", cause)
            call.respondError(HttpStatusCode.InternalServerError, HttpStatusCode.InternalServerError.description)
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
    UNKNOWN_PATH_MESSAGE_TEMPLATE.format(path) +
        templates
            .map { (app, _) -> "${ApiPaths.GENERATE_PDF}/${app.first}/${app.second}" }
            .joinToString("\n") +
        templates
            .map { (app, _) -> "${ApiPaths.GENERATE_HTML}/${app.first}/${app.second}" }
            .joinToString("\n")
} else {
    UNKNOWN_PATH_MESSAGE_TEMPLATE.format(path) +
        templates
            .map { (app, _) -> "${ApiPaths.GENERATE_PDF}/${app.first}/${app.second}" }
            .joinToString("\n")
}
