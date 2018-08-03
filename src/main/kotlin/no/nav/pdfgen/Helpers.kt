package no.nav.pdfgen

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import java.time.format.DateTimeFormatter

fun registerHelpers(handlebars: Handlebars) {
    handlebars.apply {
        registerHelper("iso_to_nor_date", Helper<String> { context, _ ->
            if (context == null) return@Helper ""
            dateFormat.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(context))
        })
        registerHelper("iso_to_date", Helper<String> { context, _ ->
            if (context == null) return@Helper ""
            dateFormat.format(DateTimeFormatter.ISO_DATE.parse(context))
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

        registerHelper("capitalize", Helper<String> { context, _ ->
            if (context == null) "" else context.toLowerCase().capitalize()
        })

        registerHelper("inc", Helper<Int> { context, _ ->
            context + 1
        })

        registerHelper("formatComma", Helper<Any> { context, _ ->
            if (context == null) "" else context.toString().replace(".", ",")
        })
    }
}
