package io.micronaut.inject.annotation.repeatable

import io.micronaut.context.annotation.Property

@interface SomeOther {
    Property[] properties() default []
}
