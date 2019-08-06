package io.micronaut.docs.config.converters

import io.micronaut.context.annotation.ConfigurationProperties
import java.time.LocalDate

// tag::class[]
@ConfigurationProperties(MyConfigurationProperties.PREFIX)
class MyConfigurationProperties {
    public static final String PREFIX = "myapp"
    protected LocalDate updatedAt

    LocalDate getUpdatedAt() {
        return this.updatedAt
    }
}
// end::class[]
