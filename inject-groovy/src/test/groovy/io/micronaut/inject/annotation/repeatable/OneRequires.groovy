package io.micronaut.inject.annotation.repeatable

import io.micronaut.context.annotation.AliasFor
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires

@Requires(property = "foo")
@Executable
@interface OneRequires {

    @AliasFor(
        annotation = SomeOther,
        member = "properties"
    )
    Property[] properties() default []
}
