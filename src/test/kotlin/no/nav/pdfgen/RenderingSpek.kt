package no.nav.pdfgen

import com.fasterxml.jackson.databind.ObjectMapper
import org.amshove.kluent.shouldBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion
import java.io.ByteArrayInputStream

object RenderingSpek : Spek({
    val templates = loadTemplates()
    val objectMapper = ObjectMapper()
    describe("All pdfs should render with default values") {
        templates.map { it.key }.forEach {
            val (applicationName, templateName) = it
            it("Renders the template $templateName for application $applicationName without exceptions") {
                render(applicationName, templateName, templates, objectMapper.createObjectNode())
            }
        }
    }

    describe("All pdfs should create a PDF/A compliant file") {
        VeraGreenfieldFoundryProvider.initialise()
        val blackList = listOf<Pair<String, String>>()
        val pdfaFlavour = PDFAFlavour.PDFA_2_B
        val validator = Foundries.defaultInstance().createValidator(pdfaFlavour, false)

        templates.map { it.key }.filterNot(blackList::contains).forEach {
            val (applicationName, templateName) = it
            it("Renders the template $templateName for application $applicationName to a PDF/A compliant document") {
                val bytes = render(applicationName, templateName, templates, objectMapper.createObjectNode())
                Foundries.defaultInstance().createParser(ByteArrayInputStream(bytes)).use {
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
    }
})
