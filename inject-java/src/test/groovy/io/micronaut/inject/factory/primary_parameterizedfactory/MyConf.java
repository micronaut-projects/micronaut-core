package io.micronaut.inject.factory.primary_parameterizedfactory;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("myBean")
public class MyConf {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
