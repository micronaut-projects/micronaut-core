package io.micronaut.inject.configproperties.writeonly;


import io.micronaut.context.annotation.ConfigurationProperties;

import javax.validation.constraints.NotBlank;

@ConfigurationProperties("test")
public class WriteOnlyConfigProperties {
    private String name;

    public void setName(@NotBlank String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }
}
