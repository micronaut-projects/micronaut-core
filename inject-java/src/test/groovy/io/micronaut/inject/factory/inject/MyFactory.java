package io.micronaut.inject.factory.inject;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Factory
public class MyFactory {

    @Inject
    MyService myService;


    @Bean
    MyService myService() {
        return new MyService();
    }


    static class MyService {

    }
}
