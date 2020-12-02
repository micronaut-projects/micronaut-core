package io.micronaut.inject.configproperties.itfce;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Requires;

import javax.validation.constraints.NotBlank;

@EachProperty(value = "my.config", primary = "default")
@Requires(property = "my.config")
public interface MyEachConfig {
    @NotBlank
    String getName();
}
