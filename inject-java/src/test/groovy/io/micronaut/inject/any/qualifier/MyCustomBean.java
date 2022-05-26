package io.micronaut.inject.any.qualifier;

public class MyCustomBean {

    private final String name;

    public MyCustomBean(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
