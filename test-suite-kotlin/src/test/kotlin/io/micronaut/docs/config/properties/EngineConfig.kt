package io.micronaut.docs.config.properties

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationProperties
import java.util.*

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine")
class EngineConfig {

    @NotBlank
    var manufacturer = "Ford"
    @Min(1L)
    var cylinders: Int = 0
    var crankShaft = CrankShaft()

    @ConfigurationProperties("crank-shaft")
    class CrankShaft {
        var rodLength: Optional<Double> = Optional.empty()
    }
}
// end::class[]