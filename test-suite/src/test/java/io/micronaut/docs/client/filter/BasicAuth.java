package io.micronaut.docs.client.filter;

//tag::class[]
import io.micronaut.http.annotation.HttpFilterStereotype;

import java.lang.annotation.*;

@HttpFilterStereotype // <1>
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PARAMETER})
public @interface BasicAuth {
}
//end::class[]
