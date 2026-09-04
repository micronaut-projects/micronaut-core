package io.micronaut.docs.inject.retainable

//tag::class[]
import io.micronaut.context.annotation.AliasFor

@Limit(max = 50)
@Retention(AnnotationRetention.RUNTIME)
annotation class MaxLength(
    @get:AliasFor(annotation = Limit::class, member = "max", applyDefault = true)
    val value: Int = 50
)
//end::class[]
