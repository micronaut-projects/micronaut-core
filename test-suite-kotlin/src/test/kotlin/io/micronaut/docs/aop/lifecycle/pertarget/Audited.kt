package io.micronaut.docs.aop.lifecycle.pertarget

// tag::imports[]
import io.micronaut.aop.Around
// end::imports[]

// tag::annotation[]
@Retention(AnnotationRetention.RUNTIME)
@Around(proxyTarget = true, lazyInterceptorsPerTarget = true)
annotation class Audited
// end::annotation[]
