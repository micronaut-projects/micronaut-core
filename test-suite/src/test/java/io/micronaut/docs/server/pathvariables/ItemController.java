package io.micronaut.docs.server.pathvariables;

import io.micronaut.context.annotation.Requires;
// tag::imports[]
import io.micronaut.http.PathVariables;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.List;
// end::imports[]

@Requires(property = "spec.name", value = "ItemControllerTest")
// tag::class[]
@Controller
public class ItemController {
// end::class[]

    // tag::item[]
    @Get("/items/{id}{/page}") // <1>
    public String item(PathVariables pathVariables) { // <2>
        long id = pathVariables.getLong("id"); // <3>
        int page = pathVariables.getInt("page", 1); // <4>
        return "Item " + id + ", page " + page;
    }
    // end::item[]

    // tag::lists[]
    @Get("/tags/{tags}")
    public String tags(PathVariables pathVariables) {
        List<String> tags = pathVariables.getStrings("tags"); // <1>
        return "Tags " + tags;
    }

    @Get("/sum/{numbers}")
    public String sum(PathVariables pathVariables) {
        List<Integer> numbers = pathVariables.getList("numbers", Integer.class); // <2>
        return "Sum " + numbers.stream().mapToInt(Integer::intValue).sum();
    }
    // end::lists[]

// tag::endclass[]
}
// end::endclass[]
