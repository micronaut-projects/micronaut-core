package catalog.web;

import io.micronaut.test.routes.custom.annotation.Read;
import io.micronaut.test.routes.custom.annotation.Resource;

/**
 * A resource whose declared route {@code /items/{id}} competes with controller routes and with
 * the routes of another resource.
 */
@Resource("/items")
public class ItemResource {
    @Read("/{id}")
    String item(String id) {
        return "item " + id;
    }
}
