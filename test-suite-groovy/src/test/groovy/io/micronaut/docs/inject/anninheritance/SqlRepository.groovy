package io.micronaut.docs.inject.anninheritance

//tag::imports[]
import io.micronaut.context.annotation.AliasFor
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationMetadata
import jakarta.inject.Named
import jakarta.inject.Singleton

import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
//end::imports[]

//tag::class[]
@Inherited // <1>
@Retention(RetentionPolicy.RUNTIME)
@Requires(property = "datasource.url") // <2>
@Named // <3>
@Singleton // <4>
@interface SqlRepository {
    @AliasFor(annotation = Named.class, member = AnnotationMetadata.VALUE_MEMBER) // <5>
    String value() default "";
}
//end::class[]
