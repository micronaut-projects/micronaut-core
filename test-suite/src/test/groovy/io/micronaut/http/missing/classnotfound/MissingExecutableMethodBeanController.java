package io.micronaut.http.missing.classnotfound;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.inject.test.external.ExternalBean;

@Requires(property = "spec.name", value = "MissingExecutableMethodBeanController")
@Controller
public class MissingExecutableMethodBeanController {

    @Get("/hello-world")
    public String get() {
        return "HELLO";
    }

    @Executable
    public void doExternalBean(ExternalBean externalBean) {
    }
}
