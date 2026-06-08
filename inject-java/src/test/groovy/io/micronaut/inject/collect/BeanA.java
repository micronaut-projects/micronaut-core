package io.micronaut.inject.collect;

import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

@Singleton
@Order(10)
public class BeanA implements MyNamedBean {
    @Override
    public String name() {
        return "A";
    }
}
