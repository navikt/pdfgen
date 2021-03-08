package no.nav.pdfgen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.JsonNodeValueResolver
import com.github.jknack.handlebars.context.MapValueResolver
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import no.nav.pdfgen.template.registerNavHelpers
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object HelperSpek : Spek({
    val jsonNodeFactory = JsonNodeFactory.instance
    val env = Environment()
    val handlebars = Handlebars(ClassPathTemplateLoader()).apply {
        registerNavHelpers(this, env)
    }

    fun jsonContext(jsonNode: JsonNode): Context {
        println(ObjectMapper().writeValueAsString(jsonNode))
        return Context
            .newBuilder(jsonNode)
            .resolver(
                JsonNodeValueResolver.INSTANCE,
                MapValueResolver.INSTANCE
            )
            .build()
    }

    describe("List contains helper") {
        val template = handlebars.compile("helper_templates/contains")

        it("A array containing the field fish should result in the string IT_CONTAINS") {
            val jsonNode = jsonNodeFactory.objectNode().apply {
                putArray("list")
                    .addObject().put("fish", "test")
            }

            template.apply(jsonContext(jsonNode)).trim() shouldBeEqualTo "IT_CONTAINS"
        }

        it("A array containing the field fish, but its a false boolean should result in NO_CONTAINS") {
            val jsonNode = jsonNodeFactory.objectNode().apply {
                putArray("list")
                    .addObject().put("fish", false)
            }

            template.apply(jsonContext(jsonNode)).trim() shouldBeEqualTo "NO_CONTAINS"
        }

        it("A empty array should result in NO_CONTAINS") {
            val jsonNode = jsonNodeFactory.objectNode().apply {
                putArray("list")
            }

            template.apply(jsonContext(jsonNode)).trim() shouldBeEqualTo "NO_CONTAINS"
        }

        it("A array without the field fish should result in NO_CONTAINS") {
            val jsonNode = jsonNodeFactory.objectNode().apply {
                putArray("list")
                    .addObject().put("shark", "something")
            }

            template.apply(jsonContext(jsonNode)).trim() shouldBeEqualTo "NO_CONTAINS"
        }

        it("A array a null fish field results in IT_CONTAINS") {
            val jsonNode = jsonNodeFactory.objectNode().apply {
                putArray("list").apply {
                    addObject().putNull("fish")
                }
            }

            template.apply(jsonContext(jsonNode)).trim() shouldBeEqualTo "NO_CONTAINS"
        }

        it("A array with two nodes, where the second contains the field fish results in IT_CONTAINS") {
            val jsonNode = jsonNodeFactory.objectNode().apply {
                putArray("list").apply {
                    addObject().put("shark", "something")
                    addObject().put("fish", "test")
                }
            }

            template.apply(jsonContext(jsonNode)).trim() shouldBeEqualTo "IT_CONTAINS"
        }

        it("A array with two nodes, where the field fish contains null on the first and a normal value on the second results in IT_CONTAINS") {
            val jsonNode = jsonNodeFactory.objectNode().apply {
                putArray("list").apply {
                    addObject().putNull("fish")
                    addObject().put("fish", "test")
                }
            }

            template.apply(jsonContext(jsonNode)).trim() shouldBeEqualTo "IT_CONTAINS"
        }
    }

    describe("Any operator") {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("a", "a")
                put("b", "b")
                put("c", "c")
            }
        )
        it("Should result in empty result when a single statement fails") {
            handlebars.compileInline("{{#any d}}YES{{/any}}").apply(context) shouldBeEqualTo ""
        }

        it("Should result in a YES when a single statement is ok") {
            handlebars.compileInline("{{#any a}}YES{{/any}}").apply(context) shouldBeEqualTo "YES"
        }

        it("Should result in a YES when one of multiple statements is ok") {
            handlebars.compileInline("{{#any d e f a}}YES{{/any}}").apply(context) shouldBeEqualTo "YES"
        }

        it("Should result in a YES when the first of multiple statements is ok") {
            handlebars.compileInline("{{#any a d e f}}YES{{/any}}").apply(context) shouldBeEqualTo "YES"
        }

        it("Should result in empty result when many statements fails") {
            handlebars.compileInline("{{#any d e f g}}YES{{/any}}").apply(context) shouldBeEqualTo ""
        }
    }

    describe("Datetime formatting") {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("timestamp", "2020-03-03T10:15:30")
                put("timestampLong", "2020-10-03T10:15:30")
                put("date", "2020-02-12")
            }
        )

        it("should format as Norwegian short date and time") {
            handlebars.compileInline("{{ iso_to_nor_datetime timestamp }}").apply(context) shouldBeEqualTo "03.03.2020 10:15"
        }

        it("should format as Norwegian short date") {
            handlebars.compileInline("{{ iso_to_nor_date timestamp }}").apply(context) shouldBeEqualTo "03.03.2020"
        }

        it("should format timestamp as Norwegian long date") {
            handlebars.compileInline("{{ iso_to_long_date timestampLong }}").apply(context) shouldBeEqualTo "3. oktober 2020"
        }

        it("should format date as Norwegian long date") {
            handlebars.compileInline("{{ iso_to_long_date date }}").apply(context) shouldBeEqualTo "12. februar 2020"
        }
    }

    describe("eq") {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        it("should equal self") {
            handlebars.compileInline("{{#eq an_int an_int }}TRUE{{/eq}}").apply(context) shouldBeEqualTo "TRUE"
            handlebars.compileInline("{{#eq string_with_int string_with_int }}TRUE{{/eq}}").apply(context) shouldBeEqualTo "TRUE"
            handlebars.compileInline("{{#eq string_with_double string_with_double }}TRUE{{/eq}}").apply(context) shouldBeEqualTo "TRUE"
            handlebars.compileInline("{{#eq a_string a_string }}TRUE{{/eq}}").apply(context) shouldBeEqualTo "TRUE"
            handlebars.compileInline("{{#eq a_double a_double }}TRUE{{/eq}}").apply(context) shouldBeEqualTo "TRUE"
        }

        it("should equal to content") {
            handlebars.compileInline("{{#eq an_int \"1337\" }}TRUE{{/eq}}").apply(context) shouldBeEqualTo "TRUE"
        }

        it("should equal to content - reverse") {
            handlebars.compileInline("{{#eq \"1337\" an_int }}TRUE{{/eq}}").apply(context) shouldBeEqualTo "TRUE"
        }

        it("should equal int to string if string has same content") {
            handlebars.compileInline("{{#eq an_int string_with_int }}TRUE{{/eq}}").apply(context) shouldBeEqualTo "TRUE"
        }

        it("should equal int to string if string has same content - reverse") {
            handlebars.compileInline("{{#eq string_with_int an_int }}TRUE{{/eq}}").apply(context) shouldBeEqualTo "TRUE"
        }

        it("should equal double to string if string has same content") {
            handlebars.compileInline("{{#eq a_double string_with_double }}TRUE{{/eq}}").apply(context) shouldBeEqualTo "TRUE"
        }

        it("should equal double to string if string has same content - reverse") {
            handlebars.compileInline("{{#eq string_with_double a_double}}TRUE{{/eq}}").apply(context) shouldBeEqualTo "TRUE"
        }

        it("should not equal if not equal") {
            handlebars.compileInline("{{#eq an_int a_double}}TRUE{{else}}FALSE{{/eq}}").apply(context) shouldBeEqualTo "FALSE"
        }

        it("should not equal if null or empty") {
            handlebars.compileInline("{{#eq an_int doesnt_exist}}TRUE{{else}}FALSE{{/eq}}").apply(context) shouldBeEqualTo "FALSE"
            handlebars.compileInline("{{#eq an_int null}}TRUE{{else}}FALSE{{/eq}}").apply(context) shouldBeEqualTo "FALSE"
        }
    }

    describe("not_eq") {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        it("should return false when compared to one self") {
            handlebars.compileInline("{{#not_eq an_int an_int }}TRUE{{else}}FALSE{{/not_eq}}").apply(context) shouldBeEqualTo "FALSE"
            handlebars.compileInline("{{#not_eq string_with_int string_with_int }}TRUE{{else}}FALSE{{/not_eq}}").apply(context) shouldBeEqualTo "FALSE"
            handlebars.compileInline("{{#not_eq string_with_double string_with_double }}TRUE{{else}}FALSE{{/not_eq}}").apply(context) shouldBeEqualTo "FALSE"
            handlebars.compileInline("{{#not_eq a_string a_string }}TRUE{{else}}FALSE{{/not_eq}}").apply(context) shouldBeEqualTo "FALSE"
            handlebars.compileInline("{{#not_eq a_double a_double }}TRUE{{else}}FALSE{{/not_eq}}").apply(context) shouldBeEqualTo "FALSE"
        }

        it("should return true if different content") {
            handlebars.compileInline("{{#not_eq an_int \"1338\" }}TRUE{{/not_eq}}").apply(context) shouldBeEqualTo "TRUE"
        }

        it("should return true if different content - reverse") {
            handlebars.compileInline("{{#not_eq \"1338\" an_int }}TRUE{{/not_eq}}").apply(context) shouldBeEqualTo "TRUE"
        }

        it("should return true if different values") {
            handlebars.compileInline("{{#not_eq an_int a_double}}TRUE{{else}}FALSE{{/not_eq}}").apply(context) shouldBeEqualTo "TRUE"
        }

        it("should return true compared two null or empty") {
            handlebars.compileInline("{{#not_eq an_int doesnt_exist}}TRUE{{else}}FALSE{{/not_eq}}").apply(context) shouldBeEqualTo "TRUE"
            handlebars.compileInline("{{#not_eq an_int null}}TRUE{{else}}FALSE{{/not_eq}}").apply(context) shouldBeEqualTo "TRUE"
        }
    }

    describe("gt - greater than") {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("small_int", -1)
                put("large_int", 1337)
                put("small_double", -1.67)
                put("large_double", 1337.67)
                put("a_string", "Adam")
                put("z_string", "Zorro")
            }
        )

        it("should return false when compared to one self") {
            handlebars.compileInline("{{#gt small_int small_int }}TRUE{{else}}FALSE{{/gt}}").apply(context) shouldBeEqualTo "FALSE"
            handlebars.compileInline("{{#gt small_double small_double }}TRUE{{else}}FALSE{{/gt}}").apply(context) shouldBeEqualTo "FALSE"
            handlebars.compileInline("{{#gt a_string a_string }}TRUE{{else}}FALSE{{/gt}}").apply(context) shouldBeEqualTo "FALSE"
        }

        it("should return true when first param greater than second param") {
            handlebars.compileInline("{{#gt large_int small_int }}TRUE{{else}}FALSE{{/gt}}").apply(context) shouldBeEqualTo "TRUE"
            handlebars.compileInline("{{#gt large_double small_double }}TRUE{{else}}FALSE{{/gt}}").apply(context) shouldBeEqualTo "TRUE"
            handlebars.compileInline("{{#gt z_string a_string }}TRUE{{else}}FALSE{{/gt}}").apply(context) shouldBeEqualTo "TRUE"
        }

        it("should return false when first param less than second param") {
            handlebars.compileInline("{{#gt small_int large_int }}TRUE{{else}}FALSE{{/gt}}").apply(context) shouldBeEqualTo "FALSE"
            handlebars.compileInline("{{#gt small_double large_double }}TRUE{{else}}FALSE{{/gt}}").apply(context) shouldBeEqualTo "FALSE"
            handlebars.compileInline("{{#gt a_string z_string }}TRUE{{else}}FALSE{{/gt}}").apply(context) shouldBeEqualTo "FALSE"
        }

        it("should fail if argument are not of same type") {
            invoking {
                handlebars.compileInline("{{#gt int_string large_int }}TRUE{{else}}FALSE{{/gt}}").apply(context)
            } shouldThrow AnyException
            invoking {
                handlebars.compileInline("{{#gt small_double a_string }}TRUE{{else}}FALSE{{/gt}}").apply(context)
            } shouldThrow AnyException
            invoking {
                handlebars.compileInline("{{#gt small_double small_int }}TRUE{{else}}FALSE{{/gt}}").apply(context)
            } shouldThrow AnyException
        }

        it("should fail if comparing two null or empty") {
            invoking {
                handlebars.compileInline("{{#gt int_string noexists }}TRUE{{else}}FALSE{{/gt}}").apply(context)
            } shouldThrow AnyException
            invoking {
                handlebars.compileInline("{{#gt small_double null }}TRUE{{else}}FALSE{{/gt}}").apply(context)
            } shouldThrow AnyException
            invoking {
                handlebars.compileInline("{{#gt small_double }}TRUE{{else}}FALSE{{/gt}}").apply(context)
            } shouldThrow AnyException
        }
    }

    describe("lt - less than") {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("small_int", -1)
                put("large_int", 1337)
                put("small_double", -1.67)
                put("large_double", 1337.67)
                put("a_string", "Adam")
                put("z_string", "Zorro")
            }
        )

        it("should return false when compared to one self") {
            handlebars.compileInline("{{#lt small_int small_int }}TRUE{{else}}FALSE{{/lt}}").apply(context) shouldBeEqualTo "FALSE"
            handlebars.compileInline("{{#lt small_double small_double }}TRUE{{else}}FALSE{{/lt}}").apply(context) shouldBeEqualTo "FALSE"
            handlebars.compileInline("{{#lt a_string a_string }}TRUE{{else}}FALSE{{/lt}}").apply(context) shouldBeEqualTo "FALSE"
        }

        it("should return false when first param greater than second param") {
            handlebars.compileInline("{{#lt large_int small_int }}TRUE{{else}}FALSE{{/lt}}").apply(context) shouldBeEqualTo "FALSE"
            handlebars.compileInline("{{#lt large_double small_double }}TRUE{{else}}FALSE{{/lt}}").apply(context) shouldBeEqualTo "FALSE"
            handlebars.compileInline("{{#lt z_string a_string }}TRUE{{else}}FALSE{{/lt}}").apply(context) shouldBeEqualTo "FALSE"
        }

        it("should return true when first param less than second param") {
            handlebars.compileInline("{{#lt small_int large_int }}TRUE{{else}}FALSE{{/lt}}").apply(context) shouldBeEqualTo "TRUE"
            handlebars.compileInline("{{#lt small_double large_double }}TRUE{{else}}FALSE{{/lt}}").apply(context) shouldBeEqualTo "TRUE"
            handlebars.compileInline("{{#lt a_string z_string }}TRUE{{else}}FALSE{{/lt}}").apply(context) shouldBeEqualTo "TRUE"
        }

        it("should fail if argument are not of same type") {
            invoking {
                handlebars.compileInline("{{#gt int_string large_int }}TRUE{{else}}FALSE{{/gt}}").apply(context)
            } shouldThrow AnyException
            invoking {
                handlebars.compileInline("{{#gt small_double a_string }}TRUE{{else}}FALSE{{/gt}}").apply(context)
            } shouldThrow AnyException
            invoking {
                handlebars.compileInline("{{#gt small_double small_int }}TRUE{{else}}FALSE{{/gt}}").apply(context)
            } shouldThrow AnyException
        }

        it("should fail if comparing two null or empty") {
            invoking {
                handlebars.compileInline("{{#gt int_string noexists }}TRUE{{else}}FALSE{{/gt}}").apply(context)
            } shouldThrow AnyException
            invoking {
                handlebars.compileInline("{{#gt small_double null }}TRUE{{else}}FALSE{{/gt}}").apply(context)
            } shouldThrow AnyException
            invoking {
                handlebars.compileInline("{{#gt small_double }}TRUE{{else}}FALSE{{/gt}}").apply(context)
            } shouldThrow AnyException
        }
    }

    describe("Currency formatting") {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("beløp", 1337.69)
                put("beløp_single_decimal", 1337.6)
                put("beløp_integer", 9001)
                put("beløp_stort", 1337420.69)
                put("beløp_ganske_liten_integer", 1)
                put("beløp_kjempeliten_integer", 0)
                put("beløp_liten_integer", 10)
                put("beløp_stor_integer", 1000001)
            }
        )

        it("should format number as currency") {
            handlebars.compileInline("{{ currency_no beløp }}").apply(context) shouldBeEqualTo "1 337,69"
            handlebars.compileInline("{{ currency_no beløp_single_decimal }}").apply(context) shouldBeEqualTo "1 337,60"
            handlebars.compileInline("{{ currency_no beløp_integer }}").apply(context) shouldBeEqualTo "9 001,00"
            handlebars.compileInline("{{ currency_no beløp_stort }}").apply(context) shouldBeEqualTo "1 337 420,69"
        }

        it("should format number as currency without decimals") {
            handlebars.compileInline("{{ currency_no beløp true }}").apply(context) shouldBeEqualTo "1 337"
            handlebars.compileInline("{{ currency_no beløp_stort true }}").apply(context) shouldBeEqualTo "1 337 420"
        }

        it("should format integer to currency") {
            handlebars.compileInline("{{ int_as_currency_no beløp_integer }}").apply(context) shouldBeEqualTo "90,01"
            handlebars.compileInline("{{ int_as_currency_no beløp_liten_integer }}").apply(context) shouldBeEqualTo "0,10"
            handlebars.compileInline("{{ int_as_currency_no beløp_kjempeliten_integer }}").apply(context) shouldBeEqualTo "0,00"
            handlebars.compileInline("{{ int_as_currency_no beløp_ganske_liten_integer }}").apply(context) shouldBeEqualTo "0,01"
            handlebars.compileInline("{{ int_as_currency_no beløp_stor_integer }}").apply(context) shouldBeEqualTo "10 000,01"
        }
    }

    describe("Is defined") {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("someProperty", false)
            }
        )

        it("should output IS DEFINED if someProperty is defined") {
            handlebars.compileInline("{{#is_defined someProperty }}IS DEFINED{{/is_defined }}").apply(context) shouldBeEqualTo "IS DEFINED"
        }

        it("should output empty string if someOtherProperty is not defined") {
            handlebars.compileInline("{{#is_defined someOtherProperty }}IS DEFINED{{/is_defined }}").apply(context) shouldBeEqualTo ""
        }
    }

    describe("contains_all") {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                putArray("myList")
                    .add("FIRST_VAL")
                    .add("SECOND_VAL")
                    .add("THIRD_VAL")
                putArray("emptyList")
            }
        )

        it("should find single param that matches") {
            handlebars.compileInline("{{#contains_all myList \"FIRST_VAL\"}}FOUND!{{else}}NOTHING!{{/contains_all }}").apply(context) shouldBeEqualTo "FOUND!"
        }

        it("should find all values without order") {
            handlebars.compileInline("""{{#contains_all myList "FIRST_VAL" "THIRD_VAL" "SECOND_VAL"}}FOUND!{{else}}NOTHING!{{/contains_all }}""").apply(context) shouldBeEqualTo "FOUND!"
        }

        it("should not find if at least one did not match") {
            handlebars.compileInline("""{{#contains_all myList "FIRST_VAL" "THIRD_VAL" "UNKNOWN"}}FOUND!{{else}}NOTHING!{{/contains_all }}""").apply(context) shouldBeEqualTo "NOTHING!"
        }

        it("should not find if empty parameter") {
            handlebars.compileInline("""{{#contains_all myList ""}}FOUND!{{else}}NOTHING!{{/contains_all }}""").apply(context) shouldBeEqualTo "NOTHING!"
        }

        it("should not find if no parameter") {
            handlebars.compileInline("""{{#contains_all myList}}FOUND!{{else}}NOTHING!{{/contains_all }}""").apply(context) shouldBeEqualTo "NOTHING!"
        }

        it("should not fail if list is empty") {
            handlebars.compileInline("""{{#contains_all emptyList "UNKNOWN"}}FOUND!{{else}}NOTHING!{{/contains_all }}""").apply(context) shouldBeEqualTo "NOTHING!"
        }

        it("should throw exception if unknown list") {
            invoking { handlebars.compileInline("""{{#contains_all dontexist "UNKNOWN"}}FOUND!{{else}}NOTHING!{{/contains_all }}""").apply(context) } shouldThrow AnyException
        }
    }

    describe("Capitilize all") {
        val context = jsonContext(jsonNodeFactory.objectNode())

        it("should capitalize all uppers") {
            handlebars.compileInline("{{capitalize_names \"BRAGE BRUKER OLSEN\"}}").apply(context) shouldBeEqualTo "Brage Bruker Olsen"
        }

        it("should capitalize all lower") {
            handlebars.compileInline("{{capitalize_names \"brage bruker olsen\"}}").apply(context) shouldBeEqualTo "Brage Bruker Olsen"
        }

        it("should capitalize all when mixed upper then lower") {
            handlebars.compileInline("{{capitalize_names \"BRage BRUker OLSEn\"}}").apply(context) shouldBeEqualTo "Brage Bruker Olsen"
        }

        it("should capitalize all when mixed lower then upper") {
            handlebars.compileInline("{{capitalize_names \"brAGE bruKer oLsEn\"}}").apply(context) shouldBeEqualTo "Brage Bruker Olsen"
        }

        it("should handle multiple space") {
            handlebars.compileInline("{{capitalize_names \"   BRAGE   BRUKER   OLSEN    \"}}").apply(context) shouldBeEqualTo "Brage Bruker Olsen"
        }

        it("should capitalize names splitted by dash ") {
            handlebars.compileInline("{{capitalize_names \" BRAGE-BRUKER OLSEN \"}}").apply(context) shouldBeEqualTo "Brage-Bruker Olsen"
        }

        it("should capitalize names splitted by dash with spacec in between ") {
            handlebars.compileInline("{{capitalize_names \" BRAGE - BRUKER OLSEN \"}}").apply(context) shouldBeEqualTo "Brage-Bruker Olsen"
        }

        it("should capitalize names splitted by apostrophe") {
            handlebars.compileInline("{{capitalize_names \" O'SHEA OLSEN \"}}").apply(context) shouldBeEqualTo "O'Shea Olsen"
        }

        it("should do nothing if already capitilized") {
            handlebars.compileInline("{{capitalize_names \"Brage Bruker Olsen\"}}").apply(context) shouldBeEqualTo "Brage Bruker Olsen"
        }

        it("should do nothing if already capitilized - single word") {
            handlebars.compileInline("{{capitalize_names \"Brage\"}}").apply(context) shouldBeEqualTo "Brage"
        }
    }

    describe("breaklines") {
        val context = jsonContext(jsonNodeFactory.objectNode())

        it("Should replace \\r\\n with newline") {
            handlebars.compileInline("{{breaklines \"I pitty the fool \\r\\n Who doesn't br\"}}").apply(context) shouldBeEqualTo "I pitty the fool <br> Who doesn&#x27;t br"
        }

        it("Should replace \\n with newline") {
            handlebars.compileInline("{{breaklines \"I pitty the fool \\n Who doesn't br\"}}").apply(context) shouldBeEqualTo "I pitty the fool <br> Who doesn&#x27;t br"
        }
    }
})
