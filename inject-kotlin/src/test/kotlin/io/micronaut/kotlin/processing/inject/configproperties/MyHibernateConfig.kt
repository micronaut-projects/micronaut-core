package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.convert.format.MapFormat
import io.micronaut.core.naming.conventions.StringConvention

@ConfigurationProperties("jpa")
class MyHibernateConfig {

    @MapFormat(keyFormat = StringConvention.RAW, transformation = MapFormat.MapTransformation.FLAT)
    var properties: Map<String, String>? = null
}
