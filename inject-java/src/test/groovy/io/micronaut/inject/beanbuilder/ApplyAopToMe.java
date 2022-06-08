package io.micronaut.inject.beanbuilder;

import io.micronaut.context.env.Environment;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;

public class ApplyAopToMe {
    @Inject
    Environment environment;

    public String hello(String name) {
        Assertions.assertNotNull(environment);
        return "Hello " + name;
    }

    public String plain(String name) {
        Assertions.assertNotNull(environment);
        return "Hello " + name;
    }
}
