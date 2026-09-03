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
package io.micronaut.docs.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class EnvironmentSpec:

  @Test
  def testRunEnvironment(): Unit =
    // tag::env[]
    val applicationContext = ApplicationContext.run("test", "android")
    val environment = applicationContext.getEnvironment

    assertTrue(environment.getActiveNames.contains("test"))
    assertTrue(environment.getActiveNames.contains("android"))
    // end::env[]

    applicationContext.close()

  @Test
  def testRunEnvironmentWithProperties(): Unit =
    // tag::envProps[]
    val applicationContext = ApplicationContext.run(
      PropertySource.of(
        "test",
        Map[String, Object](
          "micronaut.server.host" -> "foo",
          "micronaut.server.port" -> Integer.valueOf(8080)
        ).asJava
      ),
      "test",
      "android"
    )
    val environment = applicationContext.getEnvironment

    assertEquals("foo", environment.getProperty("micronaut.server.host", classOf[String]).orElse("localhost"))
    // end::envProps[]

    applicationContext.close()
