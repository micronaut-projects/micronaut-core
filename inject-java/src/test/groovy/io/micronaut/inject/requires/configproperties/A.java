package io.micronaut.inject.requires.configproperties;

import io.micronaut.context.annotation.*;

@Requires(configurationProperties = TestConfig.class, method = "isEnabled")
@jakarta.inject.Singleton
public class A {
}

