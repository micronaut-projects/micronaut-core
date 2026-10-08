package io.micronaut.docs.server.pathvariables

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.http.PathVariables
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
// end::imports[]

@Requires(property = "spec.name", value = "ItemControllerSpec")
// tag::class[]
@Controller
class ItemController {
// end::class[]

    // tag::item[]
    @Get("/items/{id}{/page}") // <1>
    String item(PathVariables pathVariables) { // <2>
        long id = pathVariables.getLong("id") // <3>
        int page = pathVariables.getInt("page", 1) // <4>
        "Item $id, page $page"
    }
    // end::item[]

    // tag::lists[]
    @Get("/tags/{tags}")
    String tags(PathVariables pathVariables) {
        List<String> tags = pathVariables.getStrings("tags") // <1>
        "Tags $tags"
    }

    @Get("/sum/{numbers}")
    String sum(PathVariables pathVariables) {
        List<Integer> numbers = pathVariables.getList("numbers", Integer) // <2>
        "Sum ${numbers.sum()}"
    }
    // end::lists[]

// tag::endclass[]
}
// end::endclass[]
