package io.micronaut.inject.value.factorywithvalue;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;

@Factory
public class MyFactory {
    @Bean
    public A newA(@Value("${foo.bar}") int port) {
        return new A(port);
    }

    @Bean
    public B newB(A a, @Value("${foo.bar}") int port) {
        return new B(a, port);
    }
}
