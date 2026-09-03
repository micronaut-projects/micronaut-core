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
package io.micronaut.docs.inject.generics

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class VehicleSpec:

  @Test
  def testStartVehicle(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "VehicleGenericsSpec").asJava
    )
    try
      val vehicle = context.getBean(classOf[Vehicle])
      assertEquals("Starting V8", vehicle.start())
      assertEquals(List(6).asJava, vehicle.v6Engines.asScala.map(_.cylinders).asJava)
      assertSame(vehicle.getEngine, vehicle.getAnotherV8)
    finally context.close()
