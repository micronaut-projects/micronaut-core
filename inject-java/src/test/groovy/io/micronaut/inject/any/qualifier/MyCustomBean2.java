package io.micronaut.inject.any.qualifier;

public class MyCustomBean2 {

    private final String name;

    public MyCustomBean2(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
