package io.micronaut.docs.aop.advice.method;

import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.docs.aop.advice.MyBean;

// tag::class[]
@Factory
public class MyFactory {

    @Prototype
    @Cacheable("my-cache")
    public MyBean myBean() {
        return new MyBean();
    }
}
// end::class[]