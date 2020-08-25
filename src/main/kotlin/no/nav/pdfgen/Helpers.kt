package no.nav.pdfgen

import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import no.nav.pdfgen.domain.syfosoknader.Periode
import no.nav.pdfgen.domain.syfosoknader.PeriodeMapper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun registerNavHelpers(handlebars: Handlebars) {
    handlebars.apply {
        registerHelper("iso_to_nor_date", Helper<String> { context, _ ->
            if (context == null) return@Helper ""
            try {
                dateFormat.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parseBest(context))
            } catch (e: Exception) {
                dateFormat.format(DateTimeFormatter.ISO_DATE_TIME.parse(context))
            }
        })

        registerHelper("iso_to_nor_datetime", Helper<String> { context, _ ->
            if (context == null) return@Helper ""
            try {
                datetimeFormat.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parseBest(context))
            } catch (e: Exception) {
                datetimeFormat.format(DateTimeFormatter.ISO_DATE_TIME.parse(context))
            }
        })

        registerHelper("iso_to_date", Helper<String> { context, _ ->
            if (context == null) return@Helper ""
            dateFormat.format(DateTimeFormatter.ISO_DATE.parse(context))
        })

        registerHelper("duration", Helper<String> { context, options ->
            ChronoUnit.DAYS.between(
                LocalDate.from(DateTimeFormatter.ISO_DATE.parse(context)),
                LocalDate.from(DateTimeFormatter.ISO_DATE.parse(options.param(0)))
            )
        })

        // Expects json-objects of the form { "fom": "2018-05-20", "tom": "2018-05-29" }
        registerHelper("json_to_period", Helper<String> { context, _ ->
            if (context == null) {
                return@Helper ""
            } else {
                val periode: Periode = PeriodeMapper.jsonTilPeriode(context)
                return@Helper periode.fom!!.format(dateFormat) + " - " + periode.tom!!.format(dateFormat)
            }
        })
        registerHelper("insert_at", Helper<Any> { context, options ->
            if (context == null) return@Helper ""
            val divider = options.hash<String>("divider", " ")
            options.params
                .map { it as Int }
                .fold(context.toString()) { v, idx -> v.substring(0, idx) + divider + v.substring(idx, v.length) }
        })

        registerHelper("eq", Helper<String> { context, options ->
            if (context == options.param(0)) options.fn() else options.inverse()
        })

        registerHelper("not_eq", Helper<String> { context, options ->
            if (context != options.param(0)) options.fn() else options.inverse()
        })

        registerHelper("safe", Helper<String> { context, _ ->
            if (context == null) "" else Handlebars.SafeString(context)
        })

        registerHelper("image", Helper<String> { context, _ ->
            if (context == null) "" else images[context]
        })

        registerHelper("resource", Helper<String> { context, _ ->
            resources[context]?.toString(Charsets.UTF_8) ?: ""
        })

        registerHelper("capitalize", Helper<String> { context, _ ->
            if (context == null) "" else context.toLowerCase().capitalize()
        })

        registerHelper("inc", Helper<Int> { context, _ ->
            context + 1
        })

        registerHelper("formatComma", Helper<Any> { context, _ ->
            if (context == null) "" else context.toString().replace(".", ",")
        })

        registerHelper("any", Helper<Any> { first, options ->
            if ((listOf(first) + options.params).all { options.isFalsy(it) }) {
                options.inverse()
            } else {
                options.fn()
            }
        })

        registerHelper("contains_field", Helper<Iterable<Any>?> { list, options ->
            val checkfor = options.param(0, null as String?)

            val contains = list
                ?.map { Context.newContext(options.context, it) }
                ?.any { ctx -> !options.isFalsy(ctx.get(checkfor)) }
                ?: false

            if (contains) {
                options.fn()
            } else {
                options.inverse()
            }
        })

        registerHelper("currency_no", Helper<Any> { context, options ->
            if (context == null) return@Helper ""
            val withoutDecimals = options.param(0, false)

            val splitNumber = context.toString().split(".")

            val formattedNumber = splitNumber.first().reversed().chunked(3).joinToString(" ").reversed()
            if(withoutDecimals){
               formattedNumber
            } else {
                val decimals = splitNumber.drop(1).firstOrNull()?.let { (it + "0").substring(0, 2) } ?: "00"
                "$formattedNumber,$decimals"
            }
        })
    }
}
