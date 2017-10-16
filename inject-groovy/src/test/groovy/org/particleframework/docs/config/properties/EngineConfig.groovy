/*
 * Copyright 2017 original authors
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
package org.particleframework.docs.config.properties

// tag::imports[]
import org.hibernate.validator.constraints.NotBlank
import org.particleframework.context.annotation.ConfigurationProperties

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
    CrankShaft crankShaft

    static class CrankShaft { // <4>
        Optional<Double> rodLength // <5>
    }
}
// end::class[]
