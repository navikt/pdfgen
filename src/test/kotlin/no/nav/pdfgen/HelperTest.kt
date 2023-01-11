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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows


object HelperTest {
    val jsonNodeFactory = JsonNodeFactory.instance
    private val env = Environment()
    private val handlebars = Handlebars(ClassPathTemplateLoader()).apply {
        registerNavHelpers(this, env)
    }

    private fun jsonContext(jsonNode: JsonNode): Context {
        println(ObjectMapper().writeValueAsString(jsonNode))
        return Context
            .newBuilder(jsonNode)
            .resolver(
                JsonNodeValueResolver.INSTANCE,
                MapValueResolver.INSTANCE
            )
            .build()
    }

    @Test
    internal fun `List contains helper a array containing the field fish should result in the string IT_CONTAINS`() {
        val template = handlebars.compile("helper_templates/contains")

        val jsonNode = jsonNodeFactory.objectNode().apply {
            putArray("list")
                .addObject().put("fish", "test")
        }

        assertEquals("IT_CONTAINS",  template.apply(jsonContext(jsonNode)).trim())
    }
    @Test
    internal fun `List contains helper a array containing the field fish, but its a false boolean should result in NO_CONTAINS`() {
        val template = handlebars.compile("helper_templates/contains")

        val jsonNode = jsonNodeFactory.objectNode().apply {
            putArray("list")
                .addObject().put("fish", false)
        }

        assertEquals("NO_CONTAINS",  template.apply(jsonContext(jsonNode)).trim())
    }

    @Test
    internal fun `List contains helper a empty array should result in NO_CONTAINS`() {
        val template = handlebars.compile("helper_templates/contains")

        val jsonNode = jsonNodeFactory.objectNode().apply { putArray("list") }

        assertEquals("NO_CONTAINS",template.apply(jsonContext(jsonNode)).trim())
    }

    @Test
    internal fun `List contains helper a array without the field fish should result in NO_CONTAINS`() {
        val template = handlebars.compile("helper_templates/contains")

        val jsonNode = jsonNodeFactory.objectNode().apply {
            putArray("list")
                .addObject().put("shark", "something")
        }

        assertEquals("NO_CONTAINS",template.apply(jsonContext(jsonNode)).trim())
    }

    @Test
    internal fun `List contains helper a array a null fish field results in IT_CONTAINS`() {
        val template = handlebars.compile("helper_templates/contains")

        val jsonNode = jsonNodeFactory.objectNode().apply {
            putArray("list").apply {
                addObject().putNull("fish")
            }
        }

        assertEquals("NO_CONTAINS",template.apply(jsonContext(jsonNode)).trim())
    }
    @Test
    internal fun `List contains helper a array with two nodes, where the second contains the field fish results in IT_CONTAINS`() {
        val template = handlebars.compile("helper_templates/contains")

        val jsonNode = jsonNodeFactory.objectNode().apply {
            putArray("list").apply {
                addObject().put("shark", "something")
                addObject().put("fish", "test")
            }
        }

        assertEquals("IT_CONTAINS",template.apply(jsonContext(jsonNode)).trim())
    }
    @Test
    internal fun `List contains helper a array with two nodes, where the field fish contains null on the first and a normal value on the second results in IT_CONTAINS`() {
        val template = handlebars.compile("helper_templates/contains")

        val jsonNode = jsonNodeFactory.objectNode().apply {
            putArray("list").apply {
                addObject().putNull("fish")
                addObject().put("fish", "test")
            }
        }

        assertEquals("IT_CONTAINS",template.apply(jsonContext(jsonNode)).trim())
    }

    @Test
    internal fun `Any operator should result in empty result when a single statement fails`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("a", "a")
                put("b", "b")
                put("c", "c")
            }
        )

        assertEquals("",handlebars.compileInline("{{#any d}}YES{{/any}}").apply(context))
    }
    @Test
    internal fun `Any operator should result in a YES when a single statement is ok`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("a", "a")
                put("b", "b")
                put("c", "c")
            }
        )

        assertEquals("",handlebars.compileInline("{{#any a}}YES{{/any}}").apply(context))
    }
    @Test
    internal fun `Any operator should result in a YES when one of multiple statements is ok`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("a", "a")
                put("b", "b")
                put("c", "c")
            }
        )

        assertEquals("YES", handlebars.compileInline("{{#any d e f a}}YES{{/any}}").apply(context))
    }

    @Test
    internal fun `Any operator should result in a YES when the first of multiple statements is ok`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("a", "a")
                put("b", "b")
                put("c", "c")
            }
        )

        assertEquals("YES", handlebars.compileInline("{{#any a d e f}}YES{{/any}}").apply(context))
    }

    @Test
    internal fun `Any operator should result in empty result when many statements fails`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("a", "a")
                put("b", "b")
                put("c", "c")
            }
        )

        assertEquals("", handlebars.compileInline("{{#any d e f g}}YES{{/any}}").apply(context))
    }

    @Test
    internal fun `Datetime formatting should format as Norwegian short date and time`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("timestamp", "2020-03-03T10:15:30")
                put("timestampLong", "2020-10-03T10:15:30")
                put("date", "2020-02-12")
            }
        )

        assertEquals("03.03.2020 10:15", handlebars.compileInline("{{ iso_to_nor_datetime timestamp }}").apply(context))
    }

    @Test
    internal fun `Datetime formatting should format as Norwegian short date`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("timestamp", "2020-03-03T10:15:30")
                put("timestampLong", "2020-10-03T10:15:30")
                put("date", "2020-02-12")
            }
        )

        assertEquals("03.03.2020", handlebars.compileInline("{{ iso_to_nor_date timestamp }}").apply(context))
    }
    @Test
    internal fun `Datetime formatting should format timestamp as Norwegian long date`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("timestamp", "2020-03-03T10:15:30")
                put("timestampLong", "2020-10-03T10:15:30")
                put("date", "2020-02-12")
            }
        )

        assertEquals("3. oktober 2020", handlebars.compileInline("{{ iso_to_long_date timestampLong }}").apply(context))
    }
    @Test
    internal fun `Datetime formatting should format date as Norwegian long date`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("timestamp", "2020-03-03T10:15:30")
                put("timestampLong", "2020-10-03T10:15:30")
                put("date", "2020-02-12")
            }
        )

        assertEquals("12. februar 2020", handlebars.compileInline("{{ iso_to_long_date date }}").apply(context))
    }

    @Test
    internal fun `eq should equal self`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        assertEquals("TRUE", handlebars.compileInline("{{#eq an_int an_int }}TRUE{{/eq}}").apply(context))
        assertEquals("TRUE", handlebars.compileInline("{{#eq string_with_int string_with_int }}TRUE{{/eq}}").apply(context) )
        assertEquals("TRUE", handlebars.compileInline("{{#eq string_with_double string_with_double }}TRUE{{/eq}}").apply(context))
        assertEquals("TRUE", handlebars.compileInline("{{#eq a_string a_string }}TRUE{{/eq}}").apply(context))
        assertEquals("TRUE", handlebars.compileInline("{{#eq a_double a_double }}TRUE{{/eq}}").apply(context))
    }
    @Test
    internal fun `eq should equal self should equal to content`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        assertEquals("TRUE", handlebars.compileInline("{{#eq an_int \"1337\" }}TRUE{{/eq}}").apply(context))
    }

    @Test
    internal fun `eq should equal self should equal to content - reverse`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        assertEquals("TRUE",  handlebars.compileInline("{{#eq \"1337\" an_int }}TRUE{{/eq}}").apply(context))
    }
    @Test
    internal fun `eq should equal self should equal int to string if string has same content`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        assertEquals("TRUE", handlebars.compileInline("{{#eq an_int string_with_int }}TRUE{{/eq}}").apply(context))
    }

    @Test
    internal fun `eq should equal self should equal int to string if string has same content - reverse`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        assertEquals("TRUE", handlebars.compileInline("{{#eq string_with_int an_int }}TRUE{{/eq}}").apply(context))
    }

    @Test
    internal fun `eq should equal self should equal double to string if string has same content`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        assertEquals("TRUE",   handlebars.compileInline("{{#eq a_double string_with_double }}TRUE{{/eq}}").apply(context))
    }

    @Test
    internal fun `eq should equal self should equal double to string if string has same content - reverse`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        assertEquals("TRUE", handlebars.compileInline("{{#eq string_with_double a_double}}TRUE{{/eq}}").apply(context))
    }
    @Test
    internal fun `eq should equal self should not equal if not equal`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        assertEquals("FALSE",  handlebars.compileInline("{{#eq an_int a_double}}TRUE{{else}}FALSE{{/eq}}").apply(context))
    }

    @Test
    internal fun `eq should equal self should not equal if null or empty`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        assertEquals("FALSE", handlebars.compileInline("{{#eq an_int doesnt_exist}}TRUE{{else}}FALSE{{/eq}}").apply(context))
        assertEquals("FALSE", handlebars.compileInline("{{#eq an_int null}}TRUE{{else}}FALSE{{/eq}}").apply(context))

    }

    @Test
    internal fun `not eq should return false when compared to one self`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        assertEquals("FALSE",  handlebars.compileInline("{{#not_eq an_int an_int }}TRUE{{else}}FALSE{{/not_eq}}").apply(context))
        assertEquals("FALSE",  handlebars.compileInline("{{#not_eq string_with_int string_with_int }}TRUE{{else}}FALSE{{/not_eq}}").apply(context))
        assertEquals("FALSE",  handlebars.compileInline("{{#not_eq string_with_double string_with_double }}TRUE{{else}}FALSE{{/not_eq}}").apply(context))
        assertEquals("FALSE",    handlebars.compileInline("{{#not_eq a_string a_string }}TRUE{{else}}FALSE{{/not_eq}}").apply(context))
        assertEquals("FALSE",   handlebars.compileInline("{{#not_eq a_double a_double }}TRUE{{else}}FALSE{{/not_eq}}").apply(context))
    }
    @Test
    internal fun `not eq should return true if different content`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        assertEquals("TRUE", handlebars.compileInline("{{#not_eq an_int \"1338\" }}TRUE{{/not_eq}}").apply(context))
    }
    @Test
    internal fun `not eq should return true if different content - reverse`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        assertEquals("TRUE", handlebars.compileInline("{{#not_eq \"1338\" an_int }}TRUE{{/not_eq}}").apply(context))
    }
    @Test
    internal fun `not eq should return true if different values`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        assertEquals("TRUE",  handlebars.compileInline("{{#not_eq an_int a_double}}TRUE{{else}}FALSE{{/not_eq}}").apply(context))
    }
    @Test
    internal fun `not eq should return true compared two null or empty`() {
        val context = jsonContext(
            jsonNodeFactory.objectNode().apply {
                put("an_int", 1337)
                put("a_double", 1337.67)
                put("string_with_int", "1337")
                put("string_with_double", "1337.67")
                put("a_string", "imma string")
            }
        )

        assertEquals("TRUE",    handlebars.compileInline("{{#not_eq an_int doesnt_exist}}TRUE{{else}}FALSE{{/not_eq}}").apply(context))
        assertEquals("TRUE",  handlebars.compileInline("{{#not_eq an_int null}}TRUE{{else}}FALSE{{/not_eq}}").apply(context))
    }
    @Test
    internal fun `gt - greater than should return false when compared to one self`() {
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

        assertEquals("FALSE", handlebars.compileInline("{{#gt small_int small_int }}TRUE{{else}}FALSE{{/gt}}").apply(context))
        assertEquals("FALSE", handlebars.compileInline("{{#gt small_double small_double }}TRUE{{else}}FALSE{{/gt}}").apply(context))
        assertEquals("FALSE", handlebars.compileInline("{{#gt a_string a_string }}TRUE{{else}}FALSE{{/gt}}").apply(context))
    }
    @Test
    internal fun `gt - greater than should return true when first param greater than second param`() {
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

        assertEquals("TRUE", handlebars.compileInline("{{#gt large_int small_int }}TRUE{{else}}FALSE{{/gt}}").apply(context))
        assertEquals("TRUE", handlebars.compileInline("{{#gt large_double small_double }}TRUE{{else}}FALSE{{/gt}}").apply(context))
        assertEquals("TRUE", handlebars.compileInline("{{#gt z_string a_string }}TRUE{{else}}FALSE{{/gt}}").apply(context))
    }

    @Test
    internal fun `gt - greater than should return false when first param less than second param`() {
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

        assertEquals("FALSE", handlebars.compileInline("{{#gt small_int large_int }}TRUE{{else}}FALSE{{/gt}}").apply(context))
        assertEquals("FALSE", handlebars.compileInline("{{#gt small_double large_double }}TRUE{{else}}FALSE{{/gt}}").apply(context))
        assertEquals("FALSE", handlebars.compileInline("{{#gt a_string z_string }}TRUE{{else}}FALSE{{/gt}}").apply(context))
    }

    @Test
    internal fun `gt - greater than should fail if argument are not of same type`() {
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

        assertThrows<Exception> {  handlebars.compileInline("{{#gt int_string large_int }}TRUE{{else}}FALSE{{/gt}}").apply(context)}
        assertThrows<Exception> {  handlebars.compileInline("{{#gt small_double a_string }}TRUE{{else}}FALSE{{/gt}}").apply(context)}
        assertThrows<Exception> {  handlebars.compileInline("{{#gt small_double small_int }}TRUE{{else}}FALSE{{/gt}}").apply(context)}
    }
    @Test
    internal fun `gt - greater than should fail if comparing two null or empty`() {
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

        assertThrows<Exception> {   handlebars.compileInline("{{#gt int_string noexists }}TRUE{{else}}FALSE{{/gt}}").apply(context)}
        assertThrows<Exception> {  handlebars.compileInline("{{#gt small_double null }}TRUE{{else}}FALSE{{/gt}}").apply(context)}
        assertThrows<Exception> {   handlebars.compileInline("{{#gt small_double }}TRUE{{else}}FALSE{{/gt}}").apply(context)}
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
                put("beløp_kjempestor_integer", Int.MAX_VALUE)
                put("beløp_liten_string", "0")
                put("beløp_stor_string", "1000001")
            }
        )

        it("should format number as currency") {
            handlebars.compileInline("{{ currency_no beløp }}").apply(context) shouldBeEqualTo "1 337,69"
            handlebars.compileInline("{{ currency_no beløp_single_decimal }}").apply(context) shouldBeEqualTo "1 337,60"
            handlebars.compileInline("{{ currency_no beløp_integer }}").apply(context) shouldBeEqualTo "9 001,00"
            handlebars.compileInline("{{ currency_no beløp_stort }}").apply(context) shouldBeEqualTo "1 337 420,69"
        }

        it("should format number as currency without decimals") {
            handlebars.compileInline("{{ currency_no beløp true }}").apply(context) shouldBeEqualTo "1 337"
            handlebars.compileInline("{{ currency_no beløp_stort true }}").apply(context) shouldBeEqualTo "1 337 420"
        }

        it("should format integer to currency") {
            handlebars.compileInline("{{ int_as_currency_no beløp_integer }}").apply(context) shouldBeEqualTo "90,01"
            handlebars.compileInline("{{ int_as_currency_no beløp_liten_integer }}").apply(context) shouldBeEqualTo "0,10"
            handlebars.compileInline("{{ int_as_currency_no beløp_kjempeliten_integer }}").apply(context) shouldBeEqualTo "0,00"
            handlebars.compileInline("{{ int_as_currency_no beløp_ganske_liten_integer }}").apply(context) shouldBeEqualTo "0,01"
            handlebars.compileInline("{{ int_as_currency_no beløp_stor_integer }}").apply(context) shouldBeEqualTo "10 000,01"
            handlebars.compileInline("{{ int_as_currency_no beløp_kjempestor_integer }}").apply(context) shouldBeEqualTo "21 474 836,47"
        }

        it("should format string to currency") {
            handlebars.compileInline("{{ string_as_currency_no beløp_liten_string }}").apply(context) shouldBeEqualTo "0,00"
            handlebars.compileInline("{{ string_as_currency_no beløp_stor_string }}").apply(context) shouldBeEqualTo "10 000,01"
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

    describe("uppercase") {
        val context = jsonContext(jsonNodeFactory.objectNode())

        it("should uppercase all letters") {
            handlebars.compileInline("{{uppercase \"brage bruker olsen\"}}").apply(context) shouldBeEqualTo "BRAGE BRUKER OLSEN"
        }

        it("should uppercase all lower letters") {
            handlebars.compileInline("{{uppercase \"Brage Bruker Olsen\"}}").apply(context) shouldBeEqualTo "BRAGE BRUKER OLSEN"
        }

        it("should uppercase strings with numbers") {
            handlebars.compileInline("{{uppercase \"0553 Oslo\"}}").apply(context) shouldBeEqualTo "0553 OSLO"
        }
    }

    describe("breaklines") {
        val context = jsonContext(jsonNodeFactory.objectNode())

        it("Should replace \\r\\n with newline") {
            handlebars.compileInline("{{breaklines \"I pitty the fool \\r\\n Who doesn't br\"}}").apply(context) shouldBeEqualTo "I pitty the fool <br/> Who doesn&#x27;t br"
        }

        it("Should replace \\n with newline") {
            handlebars.compileInline("{{breaklines \"I pitty the fool \\n Who doesn't br\"}}").apply(context) shouldBeEqualTo "I pitty the fool <br/> Who doesn&#x27;t br"
        }
    }
}
