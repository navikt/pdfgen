package no.nav.pdfgen.template

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Template
import com.github.jknack.handlebars.io.FileTemplateLoader
import com.github.jknack.handlebars.io.StringTemplateSource
import io.ktor.util.*
import java.nio.file.Files
import no.nav.pdfgen.Environment
import no.nav.pdfgen.templateRoot

typealias TemplateMap = Map<Pair<String, String>, Template>

fun setupHandlebars(env: Environment) =
    Handlebars(FileTemplateLoader(templateRoot.toFile())).apply {
        registerNavHelpers(this, env)
        infiniteLoops(true)
    }

fun loadTemplates(env: Environment): TemplateMap =
    Files.list(templateRoot)
        .filter { !Files.isHidden(it) && Files.isDirectory(it) }
        .map {
            it.fileName.toString() to Files.list(it).filter { b -> b.fileName.extension == "hbs" }
        }
        .flatMap { (applicationName, templateFiles) ->
            templateFiles.map {
                val fileName = it.fileName.toString()
                val templateName = fileName.substring(0..fileName.length - 5)
                val templateBytes = Files.readAllBytes(it).toString(Charsets.UTF_8)
                val xhtml =
                    setupHandlebars(env).compile(StringTemplateSource(fileName, templateBytes))
                (applicationName to templateName) to xhtml
            }
        }
        .toList()
        .toMap()
