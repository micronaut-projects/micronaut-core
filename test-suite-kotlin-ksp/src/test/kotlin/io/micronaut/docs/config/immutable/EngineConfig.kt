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
package io.micronaut.docs.config.immutable

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable
import java.util.Optional
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine") // <1>
data class EngineConfig @ConfigurationInject // <2>
    constructor(
        @Bindable(defaultValue = "Ford") @NotBlank val manufacturer: String, // <3>
        @Min(1) val cylinders: Int, // <4>
        @NotNull val crankShaft: CrankShaft) {

    @ConfigurationProperties("crank-shaft")
    data class CrankShaft @ConfigurationInject
    constructor(// <5>
            private val rodLength: Double? // <6>
    ) {

        fun getRodLength(): Optional<Double> {
            return Optional.ofNullable(rodLength)
        }
    }
}
// end::class[]
