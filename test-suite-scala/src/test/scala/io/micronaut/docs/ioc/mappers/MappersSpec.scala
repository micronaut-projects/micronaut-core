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
package io.micronaut.docs.ioc.mappers

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.ioc.mappers.ChristmasTypes.ChristmasPresent
import io.micronaut.docs.ioc.mappers.ChristmasTypes.Present
import io.micronaut.docs.ioc.mappers.ChristmasTypes.PresentPackaging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class MappersSpec:

  @Test
  def testMappers(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "MappersSpec").asJava
    )
    try
      // tag::mappers[]
      val productMappers = context.getBean(classOf[ProductMappers])

      val productDTO = productMappers.toProductDTO(Product(
        "MacBook",
        910.50,
        "Apple"
      ))

      assertEquals("MacBook", productDTO.name)
      assertEquals("$1821.00", productDTO.price)
      assertEquals("Great Product Company", productDTO.distributor)
      // end::mappers[]
    finally context.close()

  @Test
  def testMerging(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "MappersSpec").asJava
    )
    try
      // tag::merge[]
      val mappers = context.getBean(classOf[ChristmasMappers])

      val result = mappers.merge(
        PresentPackaging(1f, "red"),
        Present(10f, "teddy bear")
      )

      assertEquals(11f, result.weight)
      assertEquals("red", result.packagingColor)
      assertEquals("teddy bear", result.`type`)
      assertEquals("Merry christmas", result.greetingCard)
      // end::merge[]
    finally context.close()

  @Test
  def testAdditionalMappers(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "MappersSpec").asJava
    )
    try
      // tag::additional[]
      val mappers = context.getBean(classOf[AdditionalMappers])

      var result = mappers.merge(
        PresentPackaging(1f, "red"),
        Present(10f, "teddy bear"),
        Card("Merry Christmas!")
      )

      assertEquals(10f, result.weight)
      assertNull(result.packagingColor)
      assertEquals("teddy bear", result.`type`)
      assertEquals("Merry Christmas!", result.greetingCard)

      result = mappers.update(
        result,
        Map[String, Object](
          "packagingColor" -> "blue",
          "christmasCard" -> "Merry Christmas!"
        ).asJava
      )

      assertEquals(10f, result.weight)
      assertEquals("blue", result.packagingColor)
      assertEquals("teddy bear", result.`type`)
      assertEquals("Merry Christmas!!!", result.greetingCard)

      result = mappers.mergeWithMergeStrategy(
        PresentPackaging(1f, "red"),
        Present(10f, "teddy bear")
      )

      assertEquals(11f, result.weight)
      assertEquals("red", result.packagingColor)
      assertEquals("teddy bear", result.`type`)
      // end::additional[]
    finally context.close()
