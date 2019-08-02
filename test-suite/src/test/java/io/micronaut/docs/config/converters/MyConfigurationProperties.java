package io.micronaut.docs.config.converters;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.time.LocalDate;

// tag::class[]
@ConfigurationProperties(MyConfigurationProperties.PREFIX)
public class MyConfigurationProperties {
    public LocalDate getUpdatedAt() {
        return this.updatedAt;
    }

    public static final String PREFIX = "myapp";
    protected LocalDate updatedAt;
}
// end::class[]