package catalog.web;

import io.micronaut.test.routes.custom.annotation.Read;
import io.micronaut.test.routes.custom.annotation.Resource;

/**
 * A second resource on {@code /items}: its declared route {@code /items/special} competes with
 * the declared {@code /items/{id}} of {@link ItemResource}.
 */
@Resource("/items")
public class ItemSpecialResource {
    @Read("/special")
    String special() {
        return "special";
    }
}
