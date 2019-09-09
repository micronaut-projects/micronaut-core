package io.micronaut.aop.named;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("config")
public class Config {

    public Config(Inner inner) {

    }

    @ConfigurationProperties("inner")
    public static class Inner {
    }
}
