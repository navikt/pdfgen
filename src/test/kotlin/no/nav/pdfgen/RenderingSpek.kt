package no.nav.pdfgen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.util.*
import no.nav.pdfgen.api.fromHtmlToDocument
import no.nav.pdfgen.api.render
import no.nav.pdfgen.pdf.createPDFA
import no.nav.pdfgen.template.loadTemplates
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.amshove.kluent.shouldBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion

@KtorExperimentalAPI
object RenderingSpek : Spek({
    val env = Environment()
    val templates = loadTemplates(env)
    val objectMapper = ObjectMapper()
    VeraGreenfieldFoundryProvider.initialise()

    describe("All pdfs should render with default values") {
        templates.map { it.key }.forEach { it ->
            val (applicationName, templateName) = it
            val node = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use { that ->
                objectMapper.readValue(that, JsonNode::class.java)
            } ?: objectMapper.createObjectNode()
            it("Renders the template $templateName for application $applicationName without exceptions") {
                render(applicationName, templateName, templates, node)
            }
        }
    }
    describe("All pdfs should create a PDF/A compliant file") {
        val blackList = listOf<Pair<String, String>>()
        val pdfaFlavour = PDFAFlavour.PDFA_2_U
        val validator = Foundries.defaultInstance().createValidator(pdfaFlavour, false)

        templates.map { it.key }.filterNot(blackList::contains).forEach {
            val (applicationName, templateName) = it
            val node = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use { that ->
                objectMapper.readValue(that, JsonNode::class.java)
            } ?: objectMapper.createObjectNode()
            it("Renders the template $templateName for application $applicationName to a PDF/A compliant document") {
                val doc = render(applicationName, templateName, templates, node)
                val bytesOut = ByteArrayOutputStream()
                createPDFA(doc!!, bytesOut, env)
                Foundries.defaultInstance().createParser(ByteArrayInputStream(bytesOut.toByteArray())).use { that ->
                    val validationResult = validator.validate(that)
                    validationResult.testAssertions
                        .filter { test -> test.status != TestAssertion.Status.PASSED }
                        .forEach { test ->
                            println(test.message)
                            println("Location ${test.location.context} ${test.location.level}")
                            println("Status ${test.status}")
                            println("Test number ${test.ruleId.testNumber}")
                        }
                    validationResult.isCompliant shouldBe true
                }
            }
        }

        it("Renders a HTML payload to a PDF/A compliant document") {
            val doc = fromHtmlToDocument(testTemplateIncludedFonts)
            val bytesOut = ByteArrayOutputStream()
            createPDFA(doc, bytesOut, env)
            Foundries.defaultInstance().createParser(ByteArrayInputStream(bytesOut.toByteArray())).use {
                val validationResult = validator.validate(it)
                validationResult.testAssertions
                    .filter { test -> test.status != TestAssertion.Status.PASSED }
                    .forEach { test ->
                        println(test.message)
                        println("Location ${test.location.context} ${test.location.level}")
                        println("Status ${test.status}")
                        println("Test number ${test.ruleId.testNumber}")
                    }
                validationResult.isCompliant shouldBe true
            }
        }
    }
})
