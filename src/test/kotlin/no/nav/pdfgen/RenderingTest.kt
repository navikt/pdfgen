package no.nav.pdfgen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.pdfgen.api.render
import no.nav.pdfgen.pdf.createPDFA
import no.nav.pdfgen.template.loadTemplates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion
import java.io.ByteArrayInputStream

internal class RenderingTest {
    private val env = Environment()
    private val templates = loadTemplates(env)
    private val objectMapper = ObjectMapper()

    @BeforeEach
    internal fun setup() {
        VeraGreenfieldFoundryProvider.initialise()
    }

    @Test
    internal fun `All pdfs should render with default values`() {
        templates.map { it.key }.forEach { it ->
            val (applicationName, templateName) = it
            val node = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use { that ->
                objectMapper.readValue(that, JsonNode::class.java)
            } ?: objectMapper.createObjectNode()
            println("Renders the template $templateName for application $applicationName without exceptions")
            render(applicationName, templateName, templates, node)
        }
    }

    @Test
    internal fun `All pdfs should create a PDFA compliant file`() {
        val blackList = listOf<Pair<String, String>>()
        val pdfaFlavour = PDFAFlavour.PDFA_2_U
        val validator = Foundries.defaultInstance().createValidator(pdfaFlavour, false)

        templates.map { it.key }.filterNot(blackList::contains).forEach {
            val (applicationName, templateName) = it
            val node = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use { that ->
                objectMapper.readValue(that, JsonNode::class.java)
            } ?: objectMapper.createObjectNode()
            println("Renders the template $templateName for application $applicationName to a PDF/A compliant document")
            val doc = render(applicationName, templateName, templates, node)
            val pdf = createPDFA(doc!!, env)
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
    internal fun `All pdfs should create a PDFA compliant file eenders a HTML payload to a PDFA compliant document`() {
        val pdfaFlavour = PDFAFlavour.PDFA_2_U
        val validator = Foundries.defaultInstance().createValidator(pdfaFlavour, false)
        val doc = testTemplateIncludedFonts
        val pdf = createPDFA(doc, env)
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
