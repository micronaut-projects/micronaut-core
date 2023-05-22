package io.micronaut.inject.vetoed;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;

@Factory
public class BeanProducer {

    @Bean
    VetoedBean2 produce() {
        return new VetoedBean2();
    }

}
