package io.micronaut.inject.dependent.factory;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.inject.dependent.TestData;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

@Factory
public class MyBean3Factory {

    public static int beanDestroyed;
    public static int destroyed;

    @Singleton
    @Bean
    MyBean3 myBean3 = new MyBean3();

    @PreDestroy
    public void destroyMyFactory() {
        TestData.DESTRUCTION_ORDER.add(MyBean3Factory.class.getSimpleName());
        destroyed++;
    }

}
