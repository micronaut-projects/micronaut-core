package io.micronaut.docs.qualifiers.annotation

// tag::imports[]
import javax.inject.Qualifier
import java.lang.annotation.Retention

import static java.lang.annotation.RetentionPolicy.RUNTIME
// end::imports[]

// tag::class[]
@Qualifier
@Retention(RUNTIME)
@interface V8 {
}
// end::class[]