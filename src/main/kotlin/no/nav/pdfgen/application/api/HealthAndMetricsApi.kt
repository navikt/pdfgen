package no.nav.pdfgen.application.api

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.util.AttributeKey
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

private val prometheusContentType = ContentType.parse("text/plain; version=0.0.4; charset=utf-8")

val isReadyKey = AttributeKey<Boolean>("isReady")

fun Routing.registerHealthApi(meterRegistry: PrometheusMeterRegistry) {
    get(ApiPaths.IS_ALIVE) {
        // always return success for liveness
        call.respondText("I'm alive")
    }

    get(ApiPaths.IS_READY) {
        val isReady = call.application.attributes.getOrNull(isReadyKey) ?: false

        if (isReady) {
            call.respondText("I'm ready")
        } else {
            call.respondText(
                "Please wait! I'm not ready :(",
                status = HttpStatusCode.ServiceUnavailable,
            )
        }
    }

    get(ApiPaths.PROMETHEUS) {
        val names = call.request.queryParameters
            .getAll("name[]")
            ?.toSet()
            .orEmpty()

        val metrics = filterMetricFamilies(
            scrape = meterRegistry.scrape(),
            names = names,
        )

        call.respondText(
            text = metrics,
            contentType = prometheusContentType,
        )
    }
}

/**
 * Filters a Prometheus text-format scrape to the requested metric families.
 *
 * A name can match a family name from its `# HELP` line or an exported sample name such as
 * `_count`, `_sum`, or `_bucket`. If any name matches, the filter keeps the full family.
 * An empty [names] set returns the scrape unchanged.
 */
private fun filterMetricFamilies(
    scrape: String,
    names: Set<String>,
): String {
    if (names.isEmpty()) return scrape

    val selectedLines = buildList {
        val familyLines = mutableListOf<String>()
        var includeFamily = false

        scrape.lineSequence().forEach { line ->
            if (line.startsWith("# HELP ")) {
                // Flush the previous family before selecting the next one.
                if (includeFamily) addAll(familyLines)
                familyLines.clear()
                includeFamily = line.removePrefix("# HELP ").substringBefore(' ') in names
            } else if (!includeFamily && sampleName(line)?.let(names::contains) == true) {
                // Sample names may include suffixes that differ from the family name.
                includeFamily = true
            }
            familyLines.add(line)
        }
        if (includeFamily) addAll(familyLines)
    }

    return if (selectedLines.isEmpty()) "" else selectedLines.joinToString("\n", postfix = "\n")
}

private fun sampleName(line: String): String? {
    if (line.isBlank() || line.startsWith("#")) return null

    return line
        .substringBefore('{')
        .substringBefore(' ')
}
