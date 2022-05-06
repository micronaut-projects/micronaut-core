package io.micronaut.inject.dependent.factory;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;

@Prototype
@Factory
public class MyBean1Factory {

    public static int beanCreated;
    public static int destroyed;

    @Bean
    MyBean1 myBean1(MyBean2 myBean2) {
        beanCreated++;
        return new MyBean1(myBean2);
    }

}
