package io.micronaut.inject.foreach.condition;

import io.micronaut.context.annotation.EachProperty;

@EachProperty("configs")
public class XConfigMany implements XConfig {
    private String name;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }
}
