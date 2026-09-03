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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.inject.ValidatedBeanDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class EngineConfigSpec:

  @Test
  def bindsConstructorBoundConfigurationProperties(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object](
        "my.engine.cylinders" -> Integer.valueOf(8),
        "my.engine.crank-shaft.rod-length" -> java.lang.Double.valueOf(6.0d)
      ).asJava
    )
    try
      val config = context.getBean(classOf[EngineConfig])
      assertEquals(8, config.cylinders)
      assertEquals("Ford", config.manufacturer)
      assertEquals(java.lang.Double.valueOf(6.0d), config.crankShaft.rodLength)
    finally
      context.close()

  @Test
  def bindsImmutableCaseClassConfigurationProperties(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object](
        "immutable.engine.cylinders" -> Integer.valueOf(6),
        "immutable.engine.manufacturer" -> "Honda"
      ).asJava
    )
    try
      val config = context.getBean(classOf[ImmutableEngineConfig])
      assertEquals(6, config.cylinders)
      assertEquals("Honda", config.manufacturer)
    finally
      context.close()

  @Test
  def bindsScalaCollectionConfigurationProperties(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object](
        "app.names[0]" -> "alpha",
        "app.names[1]" -> "beta",
        "app.labels.one" -> "first",
        "app.labels.two" -> "second"
      ).asJava
    )
    try
      val config = context.getBean(classOf[AppConfig])
      assertEquals(List("alpha", "beta"), config.names)
      assertEquals("first", config.labels("one"))
      assertEquals("second", config.labels("two"))
    finally
      context.close()

  @Test
  def validatesConstructorBoundConfigurationProperties(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object](
        "my.engine.cylinders" -> Integer.valueOf(0),
        "my.engine.manufacturer" -> "Ford",
        "my.engine.crank-shaft.rod-length" -> java.lang.Double.valueOf(6.0d)
      ).asJava
    )
    try
      val definition = context.getBeanDefinition(classOf[EngineConfig])
      assertTrue(definition.isInstanceOf[ValidatedBeanDefinition[?]])
      val error = assertThrows(
        classOf[DependencyInjectionException],
        () => context.getBean(classOf[EngineConfig])
      )
      assertTrue(hasMessage(error, "must be greater than or equal to 1"))
    finally
      context.close()

  private def hasMessage(error: Throwable, text: String): Boolean =
    Iterator
      .iterate(error)(_.getCause)
      .takeWhile(_ != null)
      .exists(_.getMessage.contains(text))
