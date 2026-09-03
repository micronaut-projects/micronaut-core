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
package io.micronaut.docs.lifecycle

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VehicleSpec:

  @Test
  def testStartVehicle(): Unit =
    // tag::start[]
    val context = ApplicationContext.run()
    val vehicle = context.getBean(classOf[Vehicle])

    println(vehicle.start())
    // end::start[]

    try
      assertTrue(vehicle.engine.isInstanceOf[V8Engine])
      assertTrue(vehicle.engine.asInstanceOf[V8Engine].isInitialized)
    finally
      context.close()

  @Test
  def testPreDestroyBean(): Unit =
    val context = ApplicationContext.run()
    val bean = context.getBean(classOf[PreDestroyBean])
    assertFalse(bean.stopped.get())

    context.close()
    assertTrue(bean.stopped.get())

  @Test
  def testConnectionPreDestroyMethod(): Unit =
    val context = ApplicationContext.run()
    val connection = context.getBean(classOf[Connection])
    assertFalse(connection.stopped.get())

    context.close()
    assertTrue(connection.stopped.get())
