package io.micronaut.docs.qualifiers.annotationmember

// tag::imports[]
import io.micronaut.context.annotation.NonBinding
import jakarta.inject.Qualifier
import kotlin.annotation.Retention
// end::imports[]

// tag::class[]
@Qualifier // <1>
@Retention(AnnotationRetention.RUNTIME)
annotation class Cylinders(
    val value: Int,
    @get:NonBinding // <2>
    val description: String = ""
)
// end::class[]
