package io.micronaut.docs.config.converters

import io.micronaut.context.annotation.ConfigurationProperties
import java.time.LocalDate

// tag::class[]
@ConfigurationProperties(MyConfigurationProperties.PREFIX)
class MyConfigurationProperties {
    var updatedAt: LocalDate? = null
        protected set

    companion object {
        const val PREFIX = "myapp"
    }
}
// end::class[]