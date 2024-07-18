package io.micronaut.inject.foreach.withenum;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@EachProperty("config")
public record EnumConfiguration(@Parameter MyEnum myEnum,
                            @NotEmpty List<@NotBlank String> cities) {

    public enum MyEnum {
        SUMMER,
        WINTER
    }
}

