package io.micronaut.docs.inject.retainable

//tag::imports[]
import io.micronaut.core.annotation.Retainable
//end::imports[]

//tag::class[]
@Retainable // <1>
@Retention(AnnotationRetention.RUNTIME)
annotation class Limit(val min: Int = 0, val max: Int = 100)
//end::class[]
