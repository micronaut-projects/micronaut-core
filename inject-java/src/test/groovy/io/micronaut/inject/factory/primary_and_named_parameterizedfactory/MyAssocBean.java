package io.micronaut.inject.factory.primary_and_named_parameterizedfactory;

public class MyAssocBean {

    private final String name;

    public MyAssocBean(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
