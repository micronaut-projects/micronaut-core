package io.micronaut.docs.requires

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires

import javax.sql.DataSource
import java.lang.annotation.*

// tag::annotation[]
@Documented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FILE)
@Requirements(Requires(beans = [DataSource::class]), Requires(property = "datasource.url"))
annotation class RequiresJdbc
// end::annotation[]
