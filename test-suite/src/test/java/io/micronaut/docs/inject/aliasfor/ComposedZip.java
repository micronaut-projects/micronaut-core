package io.micronaut.docs.inject.aliasfor;

//tag::imports[]
import io.micronaut.context.annotation.AliasFor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
//end::imports[]

//tag::class[]
@Retention(RetentionPolicy.RUNTIME)
@Size(min = 5, max = 5) // <1>
public @interface ComposedZip {

    @AliasFor(annotation = Size.class, member = "max", applyDefault = true) // <2>
    int max() default 10;
}
//end::class[]
