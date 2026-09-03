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
package io.micronaut.docs.config.immutable

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.DependencyInjectionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class VehicleSpec:

  @Test
  def testStartVehicle(): Unit =
    // tag::start[]
    val applicationContext = ApplicationContext.run(
      Map[String, Object](
        "spec.name" -> "VehicleImmutableSpec",
        "my.engine.cylinders" -> "8",
        "my.engine.crank-shaft.rod-length" -> "7.0"
      ).asJava
    )

    val vehicle = applicationContext.getBean(classOf[Vehicle])
    println(vehicle.start())
    // end::start[]

    try
      assertEquals("Ford Engine Starting V8 [rodLength=7.0]", vehicle.start())
    finally
      applicationContext.close()

  @Test
  def testStartWithInvalidValue(): Unit =
    try
      val applicationContext = ApplicationContext.run(
        Map[String, Object](
          "spec.name" -> "VehicleImmutableSpec",
          "my.engine.cylinders" -> "-10",
          "my.engine.crank-shaft.rod-length" -> "7.0"
        ).asJava
      )
      try
        applicationContext.getBean(classOf[Vehicle])
        fail("Should have failed with a validation error")
      finally
        applicationContext.close()
    catch
      case e: DependencyInjectionException =>
        assertTrue(e.getCause.getMessage.contains("EngineConfig.cylinders - must be greater than or equal to 1"))
