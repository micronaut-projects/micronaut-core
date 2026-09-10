package io.micronaut.docs.inject.retainable;

//tag::imports[]
import io.micronaut.context.annotation.AliasFor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
//end::imports[]

//tag::class[]
@Limit(min = 5) // <1>
@Retention(RetentionPolicy.RUNTIME)
public @interface MinLength {

    @AliasFor(annotation = Limit.class, member = "min", applyDefault = true) // <2>
    int value() default 5;
}
//end::class[]
