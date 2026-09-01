package io.micronaut.docs.inject.aliasfor

@Retention(AnnotationRetention.RUNTIME)
annotation class Size(val min: Int = 0, val max: Int = 100)
