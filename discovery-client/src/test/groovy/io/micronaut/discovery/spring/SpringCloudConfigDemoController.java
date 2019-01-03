package io.micronaut.discovery.spring;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/spring-cloud/issues")
public class SpringCloudConfigDemoController {

    @Value("${environment-name:LOCAL}")
    protected String appPrefix;

    @Get("/{number}")
    String issue(Integer number) {
        return appPrefix + ": issue # " + number + "!";
    }
}