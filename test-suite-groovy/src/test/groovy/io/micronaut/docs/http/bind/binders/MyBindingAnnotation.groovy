package io.micronaut.docs.http.bind.binders;

// tag::class[]
import io.micronaut.core.bind.annotation.Bindable

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.Target

import static java.lang.annotation.RetentionPolicy.RUNTIME

@Target([ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE])
@Retention(RUNTIME)
@Bindable //<1>
@interface MyBindingAnnotation {
}
// end::class[]
