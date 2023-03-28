package io.micronaut.kotlin.processing.beans.configproperties

import kotlin.reflect.KClass

@MustBeDocumented
@Retention
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class AnnWithClass(val value: KClass<*>)
