package io.micronaut.docs.server.body;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// end::imports[]

@Requires(property = "spec.name", value = "BodyLogFilterSpec")
// tag::clazz[]
@Controller("/person")
public class BodyLogController {
    private static final Logger LOG = LoggerFactory.getLogger(BodyLogController.class);

    @Post
    void create(@Body Person person) {
        LOG.info("Creating person {}", person);
    }

    @Introspected
    record Person(String firstName, String lastName) {
    }
}
// end::clazz[]
