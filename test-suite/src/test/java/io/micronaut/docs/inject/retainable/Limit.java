package io.micronaut.docs.inject.retainable;

//tag::imports[]
import io.micronaut.core.annotation.Retainable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
//end::imports[]

//tag::class[]
@Retainable // <1>
@Retention(RetentionPolicy.RUNTIME)
public @interface Limit {
    int min() default 0;
    int max() default 100;
}
//end::class[]
