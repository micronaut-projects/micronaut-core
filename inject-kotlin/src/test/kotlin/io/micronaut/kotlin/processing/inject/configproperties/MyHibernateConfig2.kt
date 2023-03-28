package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.convert.format.MapFormat

@ConfigurationProperties("jpa")
class MyHibernateConfig2 {

    @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
    var properties: Map<String, String>? = null
}
