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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

@Disabled("pending: scala-docs-061 - Scala trait @ConfigurationProperties does not attach configuration introduction advice to the nested configuration method at runtime")
class VehicleSpec:

  @Test
  def testStartVehicle(): Unit =
    val applicationContext = ApplicationContext.run(
      Map[String, Object](
        "spec.name" -> "VehicleItfceSpec",
        "my.engine.cylinders" -> "8",
        "my.engine.crank-shaft.rod-length" -> "7.0"
      ).asJava
    )
    try
      val vehicle = applicationContext.getBean(classOf[Vehicle])
      assertEquals("Ford Engine Starting V8 [rodLength=7.0]", vehicle.start())
    finally
      applicationContext.close()

  @Test
  def testStartWithInvalidValue(): Unit =
    val applicationContext = ApplicationContext.run(
      Map[String, Object](
        "spec.name" -> "VehicleItfceSpec",
        "my.engine.cylinders" -> "-10",
        "my.engine.crank-shaft.rod-length" -> "7.0"
      ).asJava
    )
    try
      applicationContext.getBean(classOf[Vehicle])
      fail("Should have failed with a validation error")
    catch
      case e: BeanInstantiationException =>
        assertTrue(e.getMessage.contains("EngineConfig.getCylinders - must be greater than or equal to 1"))
    finally
      applicationContext.close()
