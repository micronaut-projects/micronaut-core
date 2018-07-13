package io.micronaut.inject.annotation.repeatable;

import io.micronaut.context.annotation.Property;

public @interface SomeOther {
    Property[] properties() default {};
}
