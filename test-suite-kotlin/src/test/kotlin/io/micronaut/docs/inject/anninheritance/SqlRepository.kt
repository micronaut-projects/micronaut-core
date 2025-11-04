package io.micronaut.docs.inject.anninheritance

//tag::imports[]
import io.micronaut.context.annotation.Requires
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.lang.annotation.Inherited
//end::imports[]

//tag::class[]
@Inherited // <1>
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
@Requires(property = "datasource.url") // <2>
@Named // <3>
@Singleton // <4>
annotation class SqlRepository(
    val value: String = ""
)
//end::class[]