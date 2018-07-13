package io.micronaut.inject.annotation.repeatable;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;

@Requires(property = "foo")
@Executable
public @interface OneRequires {

    @AliasFor(
        annotation = SomeOther.class,
        member = "properties"
    )
    Property[] properties() default {};
}
