package io.micronaut.inject.factory.parameterizedfactory;

import javax.inject.Singleton;

@Singleton
public class A {
    String name = "A";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
