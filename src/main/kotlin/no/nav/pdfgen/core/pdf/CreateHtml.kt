package no.nav.pdfgen.core.pdf

import com.fasterxml.jackson.databind.JsonNode
import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.JsonNodeValueResolver
import com.github.jknack.handlebars.context.MapValueResolver
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Paths
import net.logstash.logback.argument.StructuredArguments
import no.nav.pdfgen.core.HANDLEBARS_RENDERING_SUMMARY
import no.nav.pdfgen.core.objectMapper
import no.nav.pdfgen.core.template.loadTemplates

private val log = KotlinLogging.logger {}

fun createHtml(template: String, applicationName: String, jsonPayload: JsonNode): String? {
    log.debug { "${"JSON: {}"} ${objectMapper.writeValueAsString(jsonPayload)}" }
    return render(applicationName, template, jsonPayload)
}

fun createHtmlFromTemplateData(template: String, applicationName: String): String? {
    val jsonNode = hotTemplateData(applicationName, template)
    log.debug { "${"JSON: {}"} ${objectMapper.writeValueAsString(jsonNode)}" }
    return render(applicationName, template, jsonNode)
}

private fun render(applicationName: String, template: String, jsonNode: JsonNode): String? {
    return HANDLEBARS_RENDERING_SUMMARY.startTimer()
        .use {
            loadTemplates()[applicationName to template]?.apply(
                Context.newBuilder(jsonNode)
                    .resolver(
                        JsonNodeValueResolver.INSTANCE,
                        MapValueResolver.INSTANCE,
                    )
                    .build(),
            )
        }
        ?.let { html ->
            log.debug { "${"Generated HTML {}"} ${StructuredArguments.keyValue("html", html)}" }

            /* Uncomment to output html to file for easier debug
             *        File("pdf.html").bufferedWriter().use { out ->
             *            out.write(html)
             *        }
             */
            html
        }
}

private fun hotTemplateData(applicationName: String, template: String): JsonNode {
    val dataFile = Paths.get("data", applicationName, "$template.json")
    val data =
        objectMapper.readValue(
            if (Files.exists(dataFile)) {
                Files.readAllBytes(dataFile)
            } else {
                "{}".toByteArray(Charsets.UTF_8)
            },
            JsonNode::class.java,
        )
    return data
}
