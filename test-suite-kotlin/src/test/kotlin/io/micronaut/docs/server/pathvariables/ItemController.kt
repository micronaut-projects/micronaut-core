package io.micronaut.docs.server.pathvariables

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.http.PathVariables
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
// end::imports[]

@Requires(property = "spec.name", value = "ItemControllerTest")
// tag::class[]
@Controller
class ItemController {
// end::class[]

    // tag::item[]
    @Get("/items/{id}{/page}") // <1>
    fun item(pathVariables: PathVariables): String { // <2>
        val id = pathVariables.getLong("id") // <3>
        val page = pathVariables.getInt("page", 1) // <4>
        return "Item $id, page $page"
    }
    // end::item[]

    // tag::lists[]
    @Get("/tags/{tags}")
    fun tags(pathVariables: PathVariables): String {
        val tags = pathVariables.getStrings("tags") // <1>
        return "Tags $tags"
    }

    @Get("/sum/{numbers}")
    fun sum(pathVariables: PathVariables): String {
        val numbers = pathVariables.getList("numbers", Int::class.javaObjectType) // <2>
        return "Sum ${numbers.sum()}"
    }
    // end::lists[]

// tag::endclass[]
}
// end::endclass[]
