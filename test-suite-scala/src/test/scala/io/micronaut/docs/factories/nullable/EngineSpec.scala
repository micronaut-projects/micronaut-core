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
package io.micronaut.docs.factories.nullable

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class EngineSpec:

  @Test
  def testEngineNull(): Unit =
    val applicationContext = ApplicationContext.run(
      Map[String, Object](
        "engines.subaru.cylinders" -> Integer.valueOf(4),
        "engines.ford.cylinders" -> Integer.valueOf(8),
        "engines.ford.enabled" -> java.lang.Boolean.FALSE,
        "engines.lamborghini.cylinders" -> Integer.valueOf(12)
      ).asJava
    )

    try
      val engines = applicationContext.getBeansOfType(classOf[Engine])
      assertEquals(2, engines.size(), "There are 2 engines")
      val totalCylinders = engines.asScala.map(_.cylinders.intValue()).sum
      assertEquals(16, totalCylinders, "Subaru + Lamborghini equals 16 cylinders")
    finally applicationContext.close()
