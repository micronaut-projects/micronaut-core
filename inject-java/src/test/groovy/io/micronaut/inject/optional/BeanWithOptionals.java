package io.micronaut.inject.optional;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;

@Singleton
public class BeanWithOptionals {
    @Property(name = "string.prop")
    Optional<String> stringOptional;

    @Property(name = "integer.prop")
    OptionalInt optionalInt;

    @Property(name = "long.prop")
    OptionalLong optionalLong;

    @Property(name = "double.prop")
    OptionalDouble optionalDouble;


    final OptionalInt optionalIntFromConstructor;

    final OptionalLong optionalLongFromConstructor;

    final OptionalDouble optionalDoubleFromConstructor;

    public BeanWithOptionals(@Property(name = "integer.prop") OptionalInt optionalIntFromConstructor,
                             @Property(name = "long.prop") OptionalLong optionalLongFromConstructor,
                             @Property(name = "double.prop") OptionalDouble optionalDoubleFromConstructor) {
        this.optionalIntFromConstructor = optionalIntFromConstructor;
        this.optionalLongFromConstructor = optionalLongFromConstructor;
        this.optionalDoubleFromConstructor = optionalDoubleFromConstructor;
    }
}
