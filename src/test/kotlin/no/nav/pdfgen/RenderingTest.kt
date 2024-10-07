package no.nav.pdfgen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import no.nav.pdfgen.core.Environment
import no.nav.pdfgen.core.pdf.createPDFA
import no.nav.pdfgen.core.pdf.render
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion

internal class RenderingTest {
    private val templates = Environment().templates
    private val objectMapper = ObjectMapper()

    @BeforeEach
    internal fun setup() {
        VeraGreenfieldFoundryProvider.initialise()
    }

    @Test
    internal fun `All pdfs should render with default values`() {
        templates
            .map { it.key }
            .forEach {
                val (applicationName, templateName) = it
                val node =
                    javaClass
                        .getResourceAsStream("/data/$applicationName/$templateName.json")
                        ?.use { that -> objectMapper.readValue(that, JsonNode::class.java) }
                        ?: objectMapper.createObjectNode()
                println(
                    "Renders the template $templateName for application $applicationName without exceptions"
                )
                render(applicationName, templateName, node)
            }
    }

    @Test
    internal fun `All pdfs should create a PDFA compliant file`() {
        val blackList = listOf<Pair<String, String>>()
        val pdfaFlavour = PDFAFlavour.PDFA_2_U
        val validator = Foundries.defaultInstance().createValidator(pdfaFlavour, false)

        templates
            .map { it.key }
            .filterNot(blackList::contains)
            .forEach {
                val (applicationName, templateName) = it
                val node =
                    javaClass
                        .getResourceAsStream("/data/$applicationName/$templateName.json")
                        ?.use { that -> objectMapper.readValue(that, JsonNode::class.java) }
                        ?: objectMapper.createObjectNode()
                println(
                    "Renders the template $templateName for application $applicationName to a PDF/A compliant document"
                )
                val doc = render(applicationName, templateName, node)
                val pdf = createPDFA(doc!!)
                Foundries.defaultInstance().createParser(ByteArrayInputStream(pdf)).use { that ->
                    val validationResult = validator.validate(that)
                    validationResult.testAssertions
                        .filter { test -> test.status != TestAssertion.Status.PASSED }
                        .forEach { test ->
                            println(test.message)
                            println("Location ${test.location.context} ${test.location.level}")
                            println("Status ${test.status}")
                            println("Test number ${test.ruleId.testNumber}")
                        }
                    assertEquals(true, validationResult.isCompliant)
                }
            }
    }

    @Test
    internal fun `All pdfs should create a PDFA compliant file renders a HTML payload to a PDFA compliant document`() {
        val pdfaFlavour = PDFAFlavour.PDFA_2_U
        val validator = Foundries.defaultInstance().createValidator(pdfaFlavour, false)
        val doc = testTemplateIncludedFonts
        val pdf = createPDFA(doc)
        Foundries.defaultInstance().createParser(ByteArrayInputStream(pdf)).use {
            val validationResult = validator.validate(it)
            validationResult.testAssertions
                .filter { test -> test.status != TestAssertion.Status.PASSED }
                .forEach { test ->
                    println(test.message)
                    println("Location ${test.location.context} ${test.location.level}")
                    println("Status ${test.status}")
                    println("Test number ${test.ruleId.testNumber}")
                }
            assertEquals(true, validationResult.isCompliant)
        }
    }
}
