package io.micronaut.http.missing.classnotfound;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.inject.test.external.ExternalBean;

@Requires(property = "spec.name", value = "MissingConstructorBeanController")
@Controller
public class MissingConstructorBeanController {

    private final ExternalBean externalBean;

    public MissingConstructorBeanController(ExternalBean externalBean) {
        this.externalBean = externalBean;
    }

    @Get("/hello-world")
    public String get() {
        return "HELLO";
    }
}
