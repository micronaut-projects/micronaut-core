package io.micronaut.inject.dependent.factory;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.dependent.TestData;
import jakarta.annotation.PreDestroy;

@Prototype
@Factory
public class MyBean2Factory {

    public static int beanCreated;
    public static int beanDestroyed;
    public static int destroyed;

    @Bean
    MyBean2 myBean2(MyBean3 myBean3) {
        beanCreated++;
        return new MyBean2(myBean3);
    }

    @PreDestroy
    public void destroyMyFactory() {
        TestData.DESTRUCTION_ORDER.add(MyBean2Factory.class.getSimpleName());
        destroyed++;
    }

}
