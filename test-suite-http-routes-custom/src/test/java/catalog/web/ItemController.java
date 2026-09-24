package catalog.web;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Head;

/**
 * Controller routes that compete with the declared {@code /items/{id}} route.
 */
@Controller("/items")
@Requires(property = "spec.name", value = CompiledRouteSelectionTest.SPEC_NAME)
public class ItemController {

    @Get(value = "/stats", produces = MediaType.APPLICATION_JSON)
    HttpResponse<String> stats() {
        return HttpResponse.ok("controller stats").header("X-Route", "stats");
    }

    @Get(value = "/{id}", produces = MediaType.TEXT_PLAIN)
    HttpResponse<String> text(String id) {
        return HttpResponse.ok("controller text " + id).header("X-Route", "text");
    }

    @Head("/{id}")
    HttpResponse<?> head(String id) {
        return HttpResponse.ok().header("X-Route", "explicit-head");
    }
}
