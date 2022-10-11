package io.micronaut.aop.named

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("config")
class Config {

    Config(Inner inner) {

    }

    @ConfigurationProperties("inner")
    static class Inner {
    }
}
