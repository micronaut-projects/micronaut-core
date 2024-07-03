package io.micronaut.http.missing.classnotfound;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.inject.test.external.ExternalBean;
import jakarta.inject.Inject;

@Requires(property = "spec.name", value = "MissingFieldBeanController")
@Controller
public class MissingFieldBeanController {

    @Inject
    public ExternalBean externalBean;

    @Get("/hello-world")
    public String get() {
        return "HELLO";
    }
}
