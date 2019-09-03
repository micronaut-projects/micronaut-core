package io.micronaut.inject.property

import io.micronaut.context.annotation.Property
import io.micronaut.core.convert.format.MapFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BeanWithProperty {

    @set:Inject
    @setparam:Property(name="app.string")
    var stringParam:String ?= null

    @set:Inject
    @setparam:Property(name="app.map")
    @setparam:MapFormat(transformation = MapFormat.MapTransformation.FLAT)
    var mapParam:Map<String, String> ?= null

    @Property(name="app.string")
    var stringParamTwo:String ?= null

    @Property(name="app.map")
    @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
    var mapParamTwo:Map<String, String> ?= null
}
