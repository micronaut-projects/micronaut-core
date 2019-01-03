package io.micronaut.inject.property

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.convert.format.MapFormat

@ConfigurationProperties("test")
class ConfigProps {

    @setparam:MapFormat(transformation = MapFormat.MapTransformation.FLAT)
    var properties: Map<String, Any>? = null
}