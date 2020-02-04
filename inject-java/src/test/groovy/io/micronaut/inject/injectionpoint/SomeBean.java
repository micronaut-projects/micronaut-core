package io.micronaut.inject.injectionpoint;

public class SomeBean {
    private String name;

    public SomeBean(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
