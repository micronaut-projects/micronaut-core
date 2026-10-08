package io.micronaut.docs.aop.lifecycle.pertarget

// tag::imports[]
import io.micronaut.aop.Around

import java.lang.annotation.Retention

import static java.lang.annotation.RetentionPolicy.RUNTIME
// end::imports[]

// tag::annotation[]
@Retention(RUNTIME)
@Around(proxyTarget = true, lazyInterceptorsPerTarget = true)
@interface Audited {
}
// end::annotation[]
