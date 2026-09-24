package catalog.web;

import io.micronaut.test.routes.custom.annotation.Read;
import io.micronaut.test.routes.custom.annotation.Resource;

/**
 * A resource whose compiled route no other route competes with.
 */
@Resource("/docs")
public class DocResource {
    @Read("/{id}")
    String doc(String id) {
        return "doc " + id;
    }
}
