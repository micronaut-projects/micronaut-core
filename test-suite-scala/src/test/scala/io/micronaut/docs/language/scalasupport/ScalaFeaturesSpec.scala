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
package io.micronaut.docs.language.scalasupport

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.beans.BeanIntrospection
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class ScalaFeaturesSpec:

  @Test
  def scalaSpecificDocsFeaturesWork(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object](
        "spec.name" -> "ScalaLanguageSupportSpec",
        "reader.name" -> "Ada",
        "reader.favorite-genres[0]" -> "fiction",
        "reader.favorite-genres[1]" -> "history",
        "reader.labels.tier" -> "gold",
        "validated.reader.name" -> "Grace"
      ).asJava
    )
    try
      val garage = context.getBean(classOf[Garage])
      assertEquals(1, garage.engines.size)
      assertTrue(garage.enginesByName.contains("v8"))
      assertTrue(garage.selectedEngine.isDefined)

      val readerConfig = context.getBean(classOf[ReaderConfig])
      assertEquals("Ada", readerConfig.name)
      assertEquals(List("fiction", "history"), readerConfig.favoriteGenres)
      assertEquals(Map("tier" -> "gold"), readerConfig.labels)

      val validatedReaderConfig = context.getBean(classOf[ValidatedReaderConfig])
      assertEquals("Grace", validatedReaderConfig.name)

      assertNotNull(BeanIntrospection.getIntrospection(classOf[BookDto]))
    finally
      context.close()

@Requires(property = "spec.name", value = "ScalaLanguageSupportSpec")
@Singleton
@Named("v8")
class V8Engine extends Engine
