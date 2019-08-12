package io.micronaut.docs.config.mapFormat

import io.micronaut.context.annotation.ConfigurationProperties
import javax.validation.constraints.Min

// end::imports[]
import io.micronaut.core.convert.format.MapFormat
// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine")
class EngineConfig {

    @Min(1L)
    var cylinders: Int = 0
    @MapFormat(transformation = MapFormat.MapTransformation.FLAT) //<1>
    var sensors: Map<Int, String>? = null
}
// end::class[]