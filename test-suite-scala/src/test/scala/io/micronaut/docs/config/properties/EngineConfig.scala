/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.config.properties

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.jspecify.annotations.Nullable
// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine") // <1>
case class EngineConfig(
    @Bindable(defaultValue = "Ford") @NotBlank manufacturer: String, // <2> <3>
    @Min(value = 1L) cylinders: Int,
    crankShaft: EngineConfig.CrankShaft // <4>
)

object EngineConfig:
  @ConfigurationProperties("crank-shaft")
  case class CrankShaft(
      @Nullable rodLength: java.lang.Double | Null // <5>
  )
// end::class[]

@ConfigurationProperties("immutable.engine")
case class ImmutableEngineConfig(
    manufacturer: String,
    cylinders: Int
)

@ConfigurationProperties("app")
case class AppConfig(
    names: List[String],
    labels: Map[String, String]
)
