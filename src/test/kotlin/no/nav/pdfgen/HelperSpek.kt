package no.nav.pdfgen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.JsonNodeValueResolver
import com.github.jknack.handlebars.context.MapValueResolver
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import org.amshove.kluent.AnyException
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldThrow
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
            put("timestampLong", "2020-10-03T10:15:30")
            put("date", "2020-02-01")
        })

        it("should format as Norwegian short date and time") {
            handlebars.compileInline("{{ iso_to_nor_datetime timestamp }}").apply(context) shouldEqual "03.03.2020 10:15"
        }

        it("should format as Norwegian short date") {
            handlebars.compileInline("{{ iso_to_nor_date timestamp }}").apply(context) shouldEqual "03.03.2020"
        }

        it("should format timestamp as Norwegian long date") {
            handlebars.compileInline("{{ iso_to_long_date timestampLong }}").apply(context) shouldEqual "03. oktober 2020"
        }

        it("should format date as Norwegian long date") {
            handlebars.compileInline("{{ iso_to_long_date date }}").apply(context) shouldEqual "01. februar 2020"
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

        it("should format number as currency without decimals") {
            handlebars.compileInline("{{ currency_no beløp true }}").apply(context) shouldEqual "1 337"
            handlebars.compileInline("{{ currency_no beløp_stort true }}").apply(context) shouldEqual "1 337 420"
        }
    }

    describe("Is defined") {
        val context = jsonContext(jsonNodeFactory.objectNode().apply {
            put("someProperty", false)
        })

        it("should output IS DEFINED if someProperty is defined") {
            handlebars.compileInline("{{#is_defined someProperty }}IS DEFINED{{/is_defined }}").apply(context) shouldEqual "IS DEFINED"
        }

        it("should output empty string if someOtherProperty is not defined") {
            handlebars.compileInline("{{#is_defined someOtherProperty }}IS DEFINED{{/is_defined }}").apply(context) shouldEqual ""
        }
    }

    describe("contains_all") {
        val context = jsonContext(jsonNodeFactory.objectNode().apply {
            putArray("myList")
                    .add("FIRST_VAL")
                    .add("SECOND_VAL")
                    .add("THIRD_VAL")
            putArray("emptyList")
        })

        it("should find single param that matches") {
            handlebars.compileInline("{{#contains_all myList \"FIRST_VAL\"}}FOUND!{{else}}NOTHING!{{/contains_all }}").apply(context) shouldEqual "FOUND!"
        }

        it("should find all values without order") {
            handlebars.compileInline("""{{#contains_all myList "FIRST_VAL" "THIRD_VAL" "SECOND_VAL"}}FOUND!{{else}}NOTHING!{{/contains_all }}""").apply(context) shouldEqual "FOUND!"
        }

        it("should not find if at least one did not match") {
            handlebars.compileInline("""{{#contains_all myList "FIRST_VAL" "THIRD_VAL" "UNKNOWN"}}FOUND!{{else}}NOTHING!{{/contains_all }}""").apply(context) shouldEqual "NOTHING!"
        }

        it("should not find if empty parameter") {
            handlebars.compileInline("""{{#contains_all myList ""}}FOUND!{{else}}NOTHING!{{/contains_all }}""").apply(context) shouldEqual "NOTHING!"
        }

        it("should not find if no parameter") {
            handlebars.compileInline("""{{#contains_all myList}}FOUND!{{else}}NOTHING!{{/contains_all }}""").apply(context) shouldEqual "NOTHING!"
        }

        it("should not fail if list is empty") {
            handlebars.compileInline("""{{#contains_all emptyList "UNKNOWN"}}FOUND!{{else}}NOTHING!{{/contains_all }}""").apply(context) shouldEqual "NOTHING!"
        }

        it("should throw exception if unknown list") {
            invoking { handlebars.compileInline("""{{#contains_all dontexist "UNKNOWN"}}FOUND!{{else}}NOTHING!{{/contains_all }}""").apply(context) } shouldThrow AnyException
        }
    }
})
