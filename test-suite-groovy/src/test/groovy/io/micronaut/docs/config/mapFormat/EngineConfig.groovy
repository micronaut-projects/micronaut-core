package io.micronaut.docs.config.mapFormat

import io.micronaut.context.annotation.ConfigurationProperties
import javax.validation.constraints.Min

// tag::imports[]
import io.micronaut.core.convert.format.MapFormat
// end::imports[]

// tag::class[]
@ConfigurationProperties('my.engine')
class EngineConfig {

    @Min(1L)
    int cylinders

    @MapFormat(transformation = MapFormat.MapTransformation.FLAT) //<1>
    Map<Integer, String> sensors

}
// end::class[]
