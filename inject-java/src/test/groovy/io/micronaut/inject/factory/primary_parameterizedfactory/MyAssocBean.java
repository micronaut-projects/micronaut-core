package io.micronaut.inject.factory.primary_parameterizedfactory;

public class MyAssocBean {

    private final String name;

    public MyAssocBean(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
