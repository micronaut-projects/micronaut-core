package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.net.URL

@Requires(property = "spec.name", value = "ValidatedConfigurationSpec")
@ConfigurationProperties("foo.bar")
@Introspected
class ValidatedConfig {

    @NotNull
    var url: URL? = null
    @NotBlank
    var name: String? = null
}
