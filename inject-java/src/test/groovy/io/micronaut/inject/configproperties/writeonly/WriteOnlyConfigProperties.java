package io.micronaut.inject.configproperties.writeonly;


import io.micronaut.context.annotation.ConfigurationProperties;

import jakarta.validation.constraints.NotBlank;

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
