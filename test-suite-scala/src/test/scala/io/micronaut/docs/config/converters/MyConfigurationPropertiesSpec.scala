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
package io.micronaut.docs.config.converters

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import java.time.LocalDate
import scala.jdk.CollectionConverters.*

class MyConfigurationPropertiesSpec:

  @Test
  def testConvertDateFromMap(): Unit =
    // tag::runContext[]
    val ctx = ApplicationContext.run(
      Map[String, Object](
        "myapp.updatedAt" -> Map[String, Integer]( // <1>
          "day" -> Integer.valueOf(28),
          "month" -> Integer.valueOf(10),
          "year" -> Integer.valueOf(1982)
        ).asJava
      ).asJava
    )
    // end::runContext[]

    try
      val props = ctx.getBean(classOf[MyConfigurationProperties])
      assertEquals(LocalDate.of(1982, 10, 28), props.updatedAt)
    finally
      ctx.close()
