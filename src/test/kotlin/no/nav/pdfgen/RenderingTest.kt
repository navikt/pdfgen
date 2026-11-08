package no.nav.pdfgen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.pdfgen.core.Environment
import no.nav.pdfgen.core.pdf.createPDFA
import no.nav.pdfgen.core.pdf.render
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion
import java.io.ByteArrayInputStream

internal class RenderingTest {
    private val templates = Environment().templates
    private val objectMapper = ObjectMapper()
    private val requiredProfiles = listOf(PDFAFlavour.PDFA_2_A, PDFAFlavour.PDFUA_1)
    private val pdfUaExcludedTemplates = setOf("dsopkontroll" to "kontrollforespoersel")

    @BeforeEach
    internal fun setup() {
        VeraGreenfieldFoundryProvider.initialise()
    }

    @Test
    internal fun `All PDFs should render with default values`() {
        templates
            .map { it.key }
            .forEach { template ->
                val (applicationName, templateName) = template
                val node = getResourceOrNull<String>("/data/$applicationName/$templateName.json")
                    ?.let { json -> objectMapper.readValue(json, JsonNode::class.java) }
                    ?: objectMapper.createObjectNode()

                println(
                    "Renders the template $templateName for application $applicationName without exceptions",
                )

                render(applicationName, templateName, node)
            }
    }

    @Test
    internal fun `All templates should produce PDF-A-2a and supported templates PDF-UA-1 compliant documents`() {
        assertAll(
            "All templates render as PDF/A-2a, and supported templates as PDF/UA-1 compliant documents",
            templates.map { it.key }.map { (applicationName, templateName) ->
                Executable {
                    val node = getResourceOrNull<String>("/data/$applicationName/$templateName.json")
                        ?.let { json -> objectMapper.readValue(json, JsonNode::class.java) }
                        ?: objectMapper.createObjectNode()

                    val profiles = if ((applicationName to templateName) in pdfUaExcludedTemplates) {
                        listOf(PDFAFlavour.PDFA_2_A)
                    } else {
                        requiredProfiles
                    }

                    println(
                        "Validates the template $templateName for application $applicationName against $profiles",
                    )

                    val doc = requireNotNull(
                        render(
                            directoryName = applicationName,
                            template = templateName,
                            jsonNode = node,
                        ),
                    )

                    val pdf = createPDFA(doc)

                    assertCompliant(
                        pdf = pdf,
                        documentDescription = "template '$templateName' for application '$applicationName'",
                        profiles = profiles,
                    )
                }
            },
        )
    }

    @Test
    internal fun `HTML fixture should produce a PDF-A-2a and PDF-UA-1 compliant document`() {
        val doc = testTemplateIncludedFonts
        val pdf = createPDFA(doc)
        assertCompliant(pdf, "HTML fixture '/html/test_template_included_fonts.html'")
    }

    private fun assertCompliant(
        pdf: ByteArray,
        documentDescription: String,
        profiles: List<PDFAFlavour> = requiredProfiles,
    ) {
        val validationResults = profiles.map { profile ->
            val foundry = Foundries.defaultInstance()
            foundry.createValidator(profile, false).use { validator ->
                ByteArrayInputStream(pdf).use { stream ->
                    foundry.createParser(stream).use { parser ->
                        profile to validator.validate(parser)
                    }
                }
            }
        }
        val failures =
            validationResults.filterNot { (_, result) -> result.isCompliant }

        assertTrue(failures.isEmpty()) {
            failures.joinToString(separator = "\n\n") { (profile, result) ->
                val failedRules = result.testAssertions
                    .filter { assertion -> assertion.status != TestAssertion.Status.PASSED }
                    .joinToString(separator = "\n") { assertion ->
                        "Rule ${assertion.ruleId.testNumber}, status ${assertion.status}, " +
                            "location ${assertion.location.context} (level ${assertion.location.level}): " +
                            assertion.message
                    }
                "Document $documentDescription does not comply with profile $profile.\n" +
                    "Failed rules:\n$failedRules"
            }
        }
    }
}
