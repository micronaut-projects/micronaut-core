package io.micronaut.aop.named

import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
class OtherBean {

    @Inject @Named("first") public OtherInterface first
    @Inject @Named("second") public OtherInterface second
}
