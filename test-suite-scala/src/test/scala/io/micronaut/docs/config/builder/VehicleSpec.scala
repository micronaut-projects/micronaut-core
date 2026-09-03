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
package io.micronaut.docs.config.builder

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class VehicleSpec:

  @Test
  @Disabled("pending: scala-docs-041..043 - @ConfigurationBuilder does not populate Scala builder properties at runtime")
  def testStartVehicle(): Unit =
    // tag::start[]
    val properties = Map[String, Object](
      "spec.name" -> "VehicleBuilderSpec",
      "my.engine.cylinders" -> "4",
      "my.engine.manufacturer" -> "Subaru",
      "my.engine.crank-shaft.rod-length" -> java.lang.Double.valueOf(4.0d),
      "my.engine.spark-plug.name" -> "6619 LFR6AIX",
      "my.engine.spark-plug.type" -> "Iridium",
      "my.engine.spark-plug.companyName" -> "NGK"
    ).asJava
    val applicationContext = ApplicationContext.run(properties, "test")

    val vehicle = applicationContext.getBean(classOf[Vehicle])
    println(vehicle.start())
    // end::start[]

    try
      assertEquals("Subaru Engine Starting V4 [rodLength=4.0, sparkPlug=Iridium(NGK 6619 LFR6AIX)]", vehicle.start())
    finally
      applicationContext.close()
