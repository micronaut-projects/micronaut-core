package io.micronaut.test.messageBodyWriter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;

@Requires(property = "spec.name", value = "MessageBodyWriterIsWritableTest")
@Controller("/html")
public class BarController {
    @Produces(MediaType.TEXT_HTML)
    @Get("/bar")
    Bar index() {
        return new Bar("John");
    }
}
