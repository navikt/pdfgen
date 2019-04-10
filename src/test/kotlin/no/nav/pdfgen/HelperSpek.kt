package no.nav.pdfgen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.JsonNodeValueResolver
import com.github.jknack.handlebars.context.MapValueResolver
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object HelperSpek : Spek({
    val jsonNodeFactory = JsonNodeFactory.instance
    val handlebars = Handlebars(ClassPathTemplateLoader()).apply {
        registerNavHelpers(this)
    }

    fun jsonContext(jsonNode: JsonNode): Context {
        println(ObjectMapper().writeValueAsString(jsonNode))
        return Context
                .newBuilder(jsonNode)
                .resolver(JsonNodeValueResolver.INSTANCE,
                        MapValueResolver.INSTANCE)
                .build()
    }

    describe("List contains helper") {
        val template = handlebars.compile("helper_templates/contains")

        it("A array containing the field fish should result in the string IT_CONTAINS") {
            val jsonNode = jsonNodeFactory.objectNode().apply {
                putArray("list")
                        .addObject().put("fish", "test")
            }

            template.apply(jsonContext(jsonNode)).trim() shouldEqual "IT_CONTAINS"
        }

        it ("A array containing the field fish, but its a false boolean should result in NO_CONTAINS") {
            val jsonNode = jsonNodeFactory.objectNode().apply {
                putArray("list")
                        .addObject().put("fish", false)
            }

            template.apply(jsonContext(jsonNode)).trim() shouldEqual "NO_CONTAINS"
        }

        it("A empty array should result in NO_CONTAINS") {
            val jsonNode = jsonNodeFactory.objectNode().apply {
                putArray("list")
            }

            template.apply(jsonContext(jsonNode)).trim() shouldEqual "NO_CONTAINS"
        }

        it("A array without the field fish should result in NO_CONTAINS") {
            val jsonNode = jsonNodeFactory.objectNode().apply {
                putArray("list")
                        .addObject().put("shark", "something")
            }

            template.apply(jsonContext(jsonNode)).trim() shouldEqual "NO_CONTAINS"
        }
    }
})
