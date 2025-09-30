package io.micronaut.kotlin.processing.aop.named

import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
class OtherBean {

    @Inject
    @Named("first")
    lateinit var first: OtherInterface

    @Inject
    @Named("second")
    lateinit var second: OtherInterface
}
