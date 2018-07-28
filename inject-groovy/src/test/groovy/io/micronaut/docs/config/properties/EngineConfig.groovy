/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.docs.config.properties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.convert.format.MapFormat

// tag::imports[]
import org.hibernate.validator.constraints.NotBlank
import io.micronaut.context.annotation.ConfigurationProperties

import javax.validation.constraints.Min
// end::imports[]

/**
 * @author Graeme Rocher
 * @since 1.0
 */
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

    Map sensors

    void setSensors(@MapFormat(transformation = MapFormat.MapTransformation.FLAT) Map<Integer,String> sensors) {
        this.sensors = sensors
    }

}
// end::class[]
