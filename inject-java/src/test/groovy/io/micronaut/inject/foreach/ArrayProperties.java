package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.order.Ordered;

@EachProperty(value = "array", list = true)
public class ArrayProperties implements Ordered {

    private String name;
    private final int index;

    ArrayProperties(@Parameter Integer index) {
        this.index = index;
    }

    @Override
    public int getOrder() {
        return index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
