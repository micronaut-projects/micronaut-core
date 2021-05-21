package io.micronaut.inject.factory.prototype;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Prototype;

import javax.inject.Named;

@Factory
@Prototype
class PrototypeFactoryTest {
    int counter = 0;
    @Bean
    @Primary
    Result result() {
        return new Result(counter++);
    }
}

@Factory
class NonPrototypeFactoryTest {
    int counter = 0;
    @Bean
    @Named("another")
    Result result() {
        return new Result(counter++);
    }
}


class Result {
    final int val;

    Result(int val) {
        this.val = val;
    }
}
