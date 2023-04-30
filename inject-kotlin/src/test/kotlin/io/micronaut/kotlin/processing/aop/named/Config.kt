package io.micronaut.kotlin.processing.aop.named

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("config")
class Config(inner: Inner) {

    @ConfigurationProperties("inner")
    class Inner
}
