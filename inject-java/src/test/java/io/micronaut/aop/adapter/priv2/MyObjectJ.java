package io.micronaut.aop.adapter.priv2;

import io.micronaut.aop.adapter.priv2.test.TestAnnotationJ;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "InterceptorPrivate2SpecJ")
@TestAnnotationJ
@Bean
public class MyObjectJ {
    public String testMe() {
        return "bean";
    }
}
