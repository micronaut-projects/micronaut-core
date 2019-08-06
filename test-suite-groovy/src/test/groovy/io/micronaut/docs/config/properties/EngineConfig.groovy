package io.micronaut.docs.config.properties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.convert.format.MapFormat

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationProperties

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
// end::imports[]

// tag::class[]
@ConfigurationProperties('my.engine') // <1>
class EngineConfig {

    @NotBlank // <2>
    String manufacturer = "Ford" // <3>

    @Min(1L)
    int cylinders
    CrankShaft crankShaft = new CrankShaft()

    @ConfigurationProperties('crank-shaft')
    static class CrankShaft { // <4>
        Optional<Double> rodLength = Optional.empty() // <5>
    }

}
// end::class[]
