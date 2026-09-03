/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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
package io.micronaut.docs.config.mapFormat

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class VehicleSpec:

  @Test
  def testStartVehicle(): Unit =
    // tag::start[]
    val sensors = Map[Integer, String](
      Integer.valueOf(0) -> "thermostat",
      Integer.valueOf(1) -> "fuel pressure"
    ).asJava

    val map = Map[String, Object](
      "my.engine.cylinders" -> "8",
      "my.engine.sensors" -> sensors,
      "spec.name" -> "VehicleMapFormatSpec"
    ).asJava

    val applicationContext = ApplicationContext.run(map, "test")

    val vehicle = applicationContext.getBean(classOf[Vehicle])
    println(vehicle.start())
    // end::start[]

    try
      assertEquals("Engine Starting V8 [sensors=2]", vehicle.start())
    finally
      applicationContext.close()
