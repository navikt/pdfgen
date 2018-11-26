package no.nav.pdfgen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.amshove.kluent.shouldBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object RenderingSpek : Spek({
    val templates = loadTemplates()
    val objectMapper = ObjectMapper()
    VeraGreenfieldFoundryProvider.initialise()

    describe("All pdfs should render with default values") {
        templates.map { it.key }.forEach {
            val (applicationName, templateName) = it
            val node = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use {
                objectMapper.readValue(it, JsonNode::class.java)
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
            val node = javaClass.getResourceAsStream("/data/$applicationName/$templateName.json")?.use {
                objectMapper.readValue(it, JsonNode::class.java)
            } ?: objectMapper.createObjectNode()
            it("Renders the template $templateName for application $applicationName to a PDF/A compliant document") {
                val doc = render(applicationName, templateName, templates, node)
                val bytesOut = ByteArrayOutputStream()
                createPDFA(doc!!, bytesOut)
                Foundries.defaultInstance().createParser(ByteArrayInputStream(bytesOut.toByteArray())).use {
                    val validationResult = validator.validate(it)
                    validationResult.testAssertions
                            .filter { it.status != TestAssertion.Status.PASSED }
                            .forEach {
                                println(it.message)
                                println("Location ${it.location.context} ${it.location.level}")
                                println("Status ${it.status}")
                                println("Test number ${it.ruleId.testNumber}")
                            }
                    validationResult.isCompliant shouldBe true
                }
            }
        }

        it("Renders a HTML payload to a PDF/A compliant document") {
            val doc = fromHtmlToDocument(testTemplateIncludedFonts)
            val bytesOut = ByteArrayOutputStream()
            createPDFA(doc, bytesOut)
            Foundries.defaultInstance().createParser(ByteArrayInputStream(bytesOut.toByteArray())).use {
                val validationResult = validator.validate(it)
                validationResult.testAssertions
                        .filter { it.status != TestAssertion.Status.PASSED }
                        .forEach {
                            println(it.message)
                            println("Location ${it.location.context} ${it.location.level}")
                            println("Status ${it.status}")
                            println("Test number ${it.ruleId.testNumber}")
                        }
                validationResult.isCompliant shouldBe true
            }
        }
    }
})
