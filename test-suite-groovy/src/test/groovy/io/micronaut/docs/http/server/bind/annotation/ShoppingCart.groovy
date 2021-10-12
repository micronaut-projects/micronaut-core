package io.micronaut.docs.http.server.bind.annotation

// tag::class[]
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.AliasFor
import io.micronaut.core.bind.annotation.Bindable

import java.lang.annotation.Retention
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.ANNOTATION_TYPE
import static java.lang.annotation.ElementType.FIELD
import static java.lang.annotation.ElementType.PARAMETER
import static java.lang.annotation.RetentionPolicy.RUNTIME

@CompileStatic
@Target([FIELD, PARAMETER, ANNOTATION_TYPE])
@Retention(RUNTIME)
@Bindable //<1>
@interface ShoppingCart {
    @AliasFor(annotation = Bindable, member = "value")
    String value() default ""
}
// end::class[]
