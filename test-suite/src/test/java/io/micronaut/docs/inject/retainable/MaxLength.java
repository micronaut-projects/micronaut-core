package io.micronaut.docs.inject.retainable;

//tag::class[]
import io.micronaut.context.annotation.AliasFor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Limit(max = 50)
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxLength {

    @AliasFor(annotation = Limit.class, member = "max", applyDefault = true)
    int value() default 50;
}
//end::class[]
