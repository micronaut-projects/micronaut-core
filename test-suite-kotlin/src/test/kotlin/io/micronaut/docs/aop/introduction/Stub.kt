package io.micronaut.docs.aop.introduction

// tag::imports[]

import io.micronaut.aop.Introduction
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Type

import java.lang.annotation.Documented
import java.lang.annotation.Retention

import java.lang.annotation.RetentionPolicy.RUNTIME

// end::imports[]

// tag::class[]
@Introduction // <1>
@Type(StubIntroduction::class) // <2>
@Bean // <3>
@Documented
@Retention(RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class Stub(val value: String = "")
// end::class[]