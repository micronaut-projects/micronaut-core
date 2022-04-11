package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import java.net.URL

@Requires(property = "spec.name", value = "ValidatedConfigurationSpec")
@ConfigurationProperties("foo.bar")
class ValidatedConfig {

    @NotNull
    var url: URL? = null
    @NotBlank
    var name: String? = null
}
