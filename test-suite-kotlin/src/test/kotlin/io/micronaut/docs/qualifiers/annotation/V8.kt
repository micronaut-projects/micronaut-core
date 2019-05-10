package io.micronaut.docs.qualifiers.annotation

// tag::imports[]
import javax.inject.Qualifier
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy.RUNTIME
// end::imports[]
// tag::class[]
@Qualifier
@Retention(RUNTIME)
annotation class V8
// end::class[]