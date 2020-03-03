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
import java.util.Optional

// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine") // <1>
interface EngineConfig {

    @get:Bindable(defaultValue = "Ford") // <2>
    @get:NotBlank // <3>
    val manufacturer: String

    @get:Min(1L)
    val cylinders: Int

    @get:NotNull
    val crankShaft: CrankShaft // <4>

    @ConfigurationProperties("crank-shaft")
    interface CrankShaft { // <5>
        val rodLength: Double? // <6>
    }
}
// end::class[]

