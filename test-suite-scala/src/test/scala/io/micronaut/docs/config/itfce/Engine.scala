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

import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

@Requires(property = "spec.name", value = "VehicleItfceSpec")
@Singleton
class Engine(private val config: EngineConfig): // <1>
  def getCylinders: Int =
    config.getCylinders()

  def start(): String = // <2>
    s"${config.getManufacturer()} Engine Starting V${config.getCylinders()} [rodLength=${config.getCrankShaft().getRodLength().orElse(6.0d)}]"

  def getConfig: EngineConfig =
    config
