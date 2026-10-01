package io.micronaut.docs.inject.retainable

//tag::imports[]
import io.micronaut.context.annotation.AliasFor
//end::imports[]

//tag::class[]
@Limit(min = 5) // <1>
@Retention(AnnotationRetention.RUNTIME)
annotation class MinLength(
    @get:AliasFor(annotation = Limit::class, member = "min", applyDefault = true) // <2>
    val value: Int = 5
)
//end::class[]
