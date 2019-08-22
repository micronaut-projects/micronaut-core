package io.micronaut.docs.aop.around

// tag::imports[]
import io.micronaut.aop.Around
import io.micronaut.context.annotation.Type

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.Target

import static java.lang.annotation.RetentionPolicy.RUNTIME
// end::imports[]

// tag::annotation[]
@Documented
@Retention(RUNTIME) // <1>
@Target([ElementType.TYPE, ElementType.METHOD]) // <2>
@Around // <3>
@Type(NotNullInterceptor.class) // <4>
@interface NotNull {
}
// end::annotation[]
