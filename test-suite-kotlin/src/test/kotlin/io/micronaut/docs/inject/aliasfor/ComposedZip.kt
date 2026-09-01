package io.micronaut.docs.inject.aliasfor

//tag::imports[]
import io.micronaut.context.annotation.AliasFor
//end::imports[]

//tag::class[]
@Retention(AnnotationRetention.RUNTIME)
@Size(min = 5, max = 5) // <1>
annotation class ComposedZip(
    @get:AliasFor(annotation = Size::class, member = "max", applyDefault = true) // <2>
    val max: Int = 10
)
//end::class[]
