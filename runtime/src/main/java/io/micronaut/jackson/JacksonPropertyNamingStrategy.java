package io.micronaut.jackson;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;

public enum JacksonPropertyNamingStrategy {
    SNAKE_CASE(PropertyNamingStrategy.SNAKE_CASE),
    UPPER_CAMEL_CASE(PropertyNamingStrategy.UPPER_CAMEL_CASE),
    LOWER_CAMEL_CASE(PropertyNamingStrategy.LOWER_CAMEL_CASE),
    LOWER_CASE(PropertyNamingStrategy.LOWER_CASE),
    KEBAB_CASE(PropertyNamingStrategy.KEBAB_CASE);

    final PropertyNamingStrategy propertyNamingStrategy;

    JacksonPropertyNamingStrategy(PropertyNamingStrategy propertyNamingStrategy) {
        this.propertyNamingStrategy = propertyNamingStrategy;
    }
}
