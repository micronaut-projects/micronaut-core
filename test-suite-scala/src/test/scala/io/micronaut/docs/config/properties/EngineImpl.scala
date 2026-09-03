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

import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

@Requires(property = "spec.name", value = "VehiclePropertiesSpec")
// tag::class[]
@Singleton
class EngineImpl(val config: EngineConfig) extends Engine: // <1>

  override def cylinders: Int = config.cylinders

  override def start(): String = // <2>
    s"${config.manufacturer} Engine Starting V${config.cylinders} [rodLength=${Option(config.crankShaft.rodLength).getOrElse(6.0d)}]"
// end::class[]
