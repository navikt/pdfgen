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

        it("A array containing the field fish, but its a false boolean should result in NO_CONTAINS") {
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

        it("A array a null fish field results in IT_CONTAINS") {
            val jsonNode = jsonNodeFactory.objectNode().apply {
                putArray("list").apply {
                    addObject().putNull("fish")
                }
            }

            template.apply(jsonContext(jsonNode)).trim() shouldEqual "NO_CONTAINS"
        }

        it("A array with two nodes, where the second contains the field fish results in IT_CONTAINS") {
            val jsonNode = jsonNodeFactory.objectNode().apply {
                putArray("list").apply {
                    addObject().put("shark", "something")
                    addObject().put("fish", "test")
                }
            }

            template.apply(jsonContext(jsonNode)).trim() shouldEqual "IT_CONTAINS"
        }

        it("A array with two nodes, where the field fish contains null on the first and a normal value on the second results in IT_CONTAINS") {
            val jsonNode = jsonNodeFactory.objectNode().apply {
                putArray("list").apply {
                    addObject().putNull("fish")
                    addObject().put("fish", "test")
                }
            }

            template.apply(jsonContext(jsonNode)).trim() shouldEqual "IT_CONTAINS"
        }
    }

    describe("Any operator") {
        val context = jsonContext(jsonNodeFactory.objectNode().apply {
            put("a", "a")
            put("b", "b")
            put("c", "c")
        })
        it("Should result in empty result when a single statement fails") {
            handlebars.compileInline("{{#any d}}YES{{/any}}").apply(context) shouldEqual ""
        }

        it("Should result in a YES when a single statement is ok") {
            handlebars.compileInline("{{#any a}}YES{{/any}}").apply(context) shouldEqual "YES"
        }

        it("Should result in a YES when one of multiple statements is ok") {
            handlebars.compileInline("{{#any d e f a}}YES{{/any}}").apply(context) shouldEqual "YES"
        }

        it("Should result in a YES when the first of multiple statements is ok") {
            handlebars.compileInline("{{#any a d e f}}YES{{/any}}").apply(context) shouldEqual "YES"
        }

        it("Should result in empty result when many statements fails") {
            handlebars.compileInline("{{#any d e f g}}YES{{/any}}").apply(context) shouldEqual ""
        }
    }

    describe("Datetime formatting") {
        val context = jsonContext(jsonNodeFactory.objectNode().apply {
            put("timestamp", "2020-03-03T10:15:30")
        })

        it("should format as Norwegian short date and time") {
            handlebars.compileInline("{{ iso_to_nor_datetime timestamp }}").apply(context) shouldEqual "03.03.2020 10:15"
        }

        it("should format as Norwegian short date") {
            handlebars.compileInline("{{ iso_to_nor_date timestamp }}").apply(context) shouldEqual "03.03.2020"
        }
    }

    describe("Currency formatting") {
        val context = jsonContext(jsonNodeFactory.objectNode().apply {
            put("beløp", 1337.69)
            put("beløp_single_decimal", 1337.6)
            put("beløp_integer", 9001)
            put("beløp_stort", 1337420.69)
        })

        it("should format number as currency") {
            handlebars.compileInline("{{ currency_no beløp }}").apply(context) shouldEqual "1 337,69"
            handlebars.compileInline("{{ currency_no beløp_single_decimal }}").apply(context) shouldEqual "1 337,60"
            handlebars.compileInline("{{ currency_no beløp_integer }}").apply(context) shouldEqual "9 001,00"
            handlebars.compileInline("{{ currency_no beløp_stort }}").apply(context) shouldEqual "1 337 420,69"
        }
    }
})
