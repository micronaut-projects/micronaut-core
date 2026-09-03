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
package io.micronaut.docs.config.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.qualifiers.Qualifiers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import java.net.URI
import scala.jdk.CollectionConverters.*

class EachPropertyTest:

  @Test
  def testEachProperty(): Unit =
    // tag::config[]
    val applicationContext = ApplicationContext.run(PropertySource.of(
      "test",
      Map[String, Object](
        "test.datasource.one.url" -> "jdbc:mysql://localhost/one",
        "test.datasource.two.url" -> "jdbc:mysql://localhost/two"
      ).asJava
    ))
    // end::config[]
    try
      // tag::beans[]
      val beansOfType = applicationContext.getBeansOfType(classOf[DataSourceConfiguration])
      assertEquals(2, beansOfType.size()) // <1>

      val firstConfig = applicationContext.getBean(
        classOf[DataSourceConfiguration],
        Qualifiers.byName("one") // <2>
      )

      assertEquals(URI.create("jdbc:mysql://localhost/one"), firstConfig.url)
      // end::beans[]
    finally
      applicationContext.close()

  @Test
  def testEachPropertyList(): Unit =
    val applicationContext = ApplicationContext.run(
      Map[String, Object](
        "ratelimits" -> List(
          Map[String, Object]("period" -> "10s", "limit" -> "1000").asJava,
          Map[String, Object]("period" -> "1m", "limit" -> "5000").asJava
        ).asJava
      ).asJava
    )
    try
      val beansOfType = applicationContext.streamOfType(classOf[RateLimitsConfiguration]).toList

      assertEquals(2, beansOfType.size())
      assertEquals(1000, beansOfType.get(0).limit)
      assertEquals(5000, beansOfType.get(1).limit)
    finally
      applicationContext.close()
