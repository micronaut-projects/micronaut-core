package io.micronaut.inject.configproperties.other;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("parent")
public class ParentConfigProperties {

    private String name;

    protected String nationality;

    public String getName() {
        return name;
    }

    protected void setName(String name) {
        this.name = name;
    }

}
