/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
