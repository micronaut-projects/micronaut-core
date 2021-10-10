package io.micronaut.docs.writable

//tag::imports[]

import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import io.micronaut.core.io.Writable
import io.micronaut.core.util.CollectionUtils
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.exceptions.HttpServerException
import java.io.Writer

//end::imports[]

//tag::clazz[]
@Controller("/template")
class TemplateController {

    private val templateEngine = SimpleTemplateEngine()
    private val template: Template

    init {
        template = initTemplate() // <1>
    }

    @Get(value = "/welcome", produces = [MediaType.TEXT_PLAIN])
    internal fun render(): Writable { // <2>
        return { writer: Writer ->
            val writable = template.make( // <3>
                    CollectionUtils.mapOf(
                            "firstName", "Fred",
                            "lastName", "Flintstone"
                    )
            )
            writable.writeTo(writer)
        } as Writable
    }

    private fun initTemplate(): Template {
        val template: Template
        try {
            template = templateEngine.createTemplate(
                    "Dear \$firstName \$lastName. Nice to meet you."
            )
        } catch (e: Exception) {
            throw HttpServerException("Cannot create template")
        }

        return template
    }
}
//end::clazz[]