package io.micronaut.docs.aop.around

// tag::imports[]

import io.micronaut.aop.Around
import io.micronaut.context.annotation.Type

import java.lang.annotation.Documented
import java.lang.annotation.Retention

import java.lang.annotation.RetentionPolicy.RUNTIME

// end::imports[]

// tag::annotation[]
@Documented
@Retention(RUNTIME) // <1>
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER) // <2>
@Around // <3>
@Type(NotNullInterceptor::class) // <4>
annotation class NotNull
// end::annotation[]
