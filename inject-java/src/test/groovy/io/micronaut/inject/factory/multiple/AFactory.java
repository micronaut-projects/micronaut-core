package io.micronaut.inject.factory.multiple;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;

@Factory
class AFactory {

    @Bean
    @Requires(beans=X.class, missingBeans=Y.class)
    A a(X x) {
        return new A();
    }

    @Bean
    @Requires(beans= {X.class, Y.class})
    A a(X x, Y y) {
        return new A();
    }
}
