package io.micronaut.aop.adapter.priv3;

import io.micronaut.aop.adapter.priv3.test1.TestAnnotation1J;
import io.micronaut.aop.adapter.priv3.test2.TestAnnotation2J;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "InterceptorPrivate3SpecJ")
@TestAnnotation1J
@TestAnnotation2J
@Bean
public class MyObjectJ {
    public String testMe() {
        return "bean";
    }
}
