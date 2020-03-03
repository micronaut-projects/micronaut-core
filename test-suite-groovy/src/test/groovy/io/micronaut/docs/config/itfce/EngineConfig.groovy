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
package io.micronaut.docs.config.itfce

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable
import javax.validation.constraints.*

// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine") // <1>
interface EngineConfig {

    @Bindable(defaultValue = "Ford") // <2>
    @NotBlank // <3>
    String getManufacturer()

    @Min(1L)
    int getCylinders()

    @NotNull
    CrankShaft getCrankShaft() // <4>

    @ConfigurationProperties("crank-shaft")
    static interface CrankShaft { // <5>
        Optional<Double> getRodLength() // <6>
    }
}
// end::class[]

