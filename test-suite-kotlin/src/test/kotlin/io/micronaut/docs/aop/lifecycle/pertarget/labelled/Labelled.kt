package io.micronaut.docs.aop.lifecycle.pertarget.labelled

import io.micronaut.aop.Around

@Retention(AnnotationRetention.RUNTIME)
@Around(proxyTarget = true, lazyInterceptorsPerTarget = true)
annotation class Labelled
