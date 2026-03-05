package io.micronaut.inject.configproperties.condition.data;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("demo")
public interface DemoConfig {
    String getName();
    Integer getCount();
}
