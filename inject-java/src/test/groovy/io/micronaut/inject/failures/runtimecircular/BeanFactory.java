package io.micronaut.inject.failures.runtimecircular;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;

@Factory
public class BeanFactory {

    @Bean
    A a(BeanContext ctx) {
        return new A(ctx.getBean(B.class));
    }

    @Bean
    B b(BeanContext ctx) {
        return new B(ctx.getBean(A.class));
    }

}
