package io.micronaut.inject.collect;

import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

@Singleton
@Order(20)
public class BeanB implements MyNamedBean {
    @Override
    public String name() {
        return "B";
    }
}
