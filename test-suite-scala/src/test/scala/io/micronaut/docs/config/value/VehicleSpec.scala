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
package io.micronaut.docs.config.value

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class VehicleSpec:

  private val config = Map[String, Object]("spec.name" -> "VehicleValueSpec").asJava

  @Test
  def testStartVehicleWithConfiguration(): Unit =
    val applicationContext = ApplicationContext.builder("test").properties(config).build()
    try
      val map = Map[String, Object]("my.engine.cylinders" -> "8").asJava
      applicationContext.getEnvironment.addPropertySource(PropertySource.of("test", map))
      applicationContext.start()

      val vehicle = applicationContext.getBean(classOf[Vehicle])
      assertEquals("Starting V8 Engine", vehicle.start())
    finally
      applicationContext.close()

  @Test
  def testStartVehicleWithoutConfiguration(): Unit =
    val applicationContext = ApplicationContext.builder("test").properties(config).build()
    try
      applicationContext.start()

      val vehicle = applicationContext.getBean(classOf[Vehicle])
      assertEquals("Starting V6 Engine", vehicle.start())
    finally
      applicationContext.close()

  @Test
  def testStartVehicleWithNonEmptyPlaceholder(): Unit =
    val applicationContext = ApplicationContext.builder("test").properties(config).build()
    try
      val map = Map[String, Object](
        "my.engine.description" -> "${DESCRIPTION}",
        "DESCRIPTION" -> "V8 Engine"
      ).asJava
      applicationContext.getEnvironment.addPropertySource(PropertySource.of("test", map))
      applicationContext.start()

      val vehicle = applicationContext.getBean(classOf[Vehicle])
      assertEquals("V8 Engine", vehicle.getEngine.getDescription())
    finally
      applicationContext.close()

  @Test
  def testStartVehicleWithEmptyPlaceholder(): Unit =
    val applicationContext = ApplicationContext.builder("test").properties(config).build()
    try
      val map = Map[String, Object]("my.engine.description" -> "${DESCRIPTION}").asJava
      applicationContext.getEnvironment.addPropertySource(PropertySource.of("test", map))
      applicationContext.start()

      val vehicle = applicationContext.getBean(classOf[Vehicle])
      assertNull(vehicle.getEngine.getDescription())
    finally
      applicationContext.close()
