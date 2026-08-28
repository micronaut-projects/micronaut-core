package io.micronaut.inject.any.qualifier;

import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.naming.Named;
import jakarta.inject.Singleton;

@Factory
@Requires(property = "spec.name", value = "AnyQualifierSpec")
public class MyAnyFactory {

    @Any
    @Bean
    @Singleton
    MyCustomBean buildSingletonBean(Qualifier qualifier) {
        return new MyCustomBean(((Named) qualifier).getName());
    }

    @Any
    @Bean
    @Prototype
    MyCustomBean2 buildPrototypeBean(Qualifier qualifier) {
        return new MyCustomBean2(((Named) qualifier).getName());
    }

}
