package io.micronaut.inject.requires.configproperties;

import io.micronaut.context.annotation.Requires;

@Requires(configurationProperties = TestConfig.InnerConfig.class, method = "isEnabled")
@jakarta.inject.Singleton
public class B
{
}

