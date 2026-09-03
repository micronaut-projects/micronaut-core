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
import io.micronaut.docs.config.env.DataSourceFactory.DataSource
import io.micronaut.inject.qualifiers.Qualifiers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class EachBeanTest:

  @Test
  def testEachBean(): Unit =
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
      val beansOfType = applicationContext.getBeansOfType(classOf[DataSource])
      assertEquals(2, beansOfType.size()) // <1>

      val firstConfig = applicationContext.getBean(
        classOf[DataSource],
        Qualifiers.byName("one") // <2>
      )
      // end::beans[]

      assertNotNull(firstConfig)
    finally
      applicationContext.close()
