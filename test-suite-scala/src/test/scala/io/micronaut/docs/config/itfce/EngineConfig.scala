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
package io.micronaut.docs.config.itfce

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.Optional
// end::imports[]

@Requires(property = "spec.name", value = "VehicleItfceSpec")
// tag::class[]
@ConfigurationProperties("my.engine") // <1>
trait EngineConfig:

  @Bindable(defaultValue = "Ford") // <2>
  @NotBlank // <3>
  def getManufacturer(): String

  @Min(value = 1L)
  def getCylinders(): Int

  @NotNull
  def getCrankShaft(): EngineConfig.CrankShaft // <4>

object EngineConfig:
  @ConfigurationProperties("crank-shaft")
  trait CrankShaft: // <5>
    def getRodLength(): Optional[java.lang.Double] // <6>
// end::class[]
