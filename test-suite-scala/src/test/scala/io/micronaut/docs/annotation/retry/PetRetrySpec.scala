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
package io.micronaut.docs.annotation.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.retry.annotation.Fallback
import io.micronaut.retry.annotation.Retryable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

import scala.jdk.CollectionConverters.*

class PetRetrySpec:

  @Test
  def retryClientAndFallbackAreAvailable(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "PetRetrySpec").asJava
    )
    try
      val clientDefinition = context.getBeanDefinition(classOf[PetClient])
      assertTrue(clientDefinition.hasAnnotation(classOf[Retryable]))

      val fallback = context.getBean(classOf[PetFallback])
      assertTrue(context.getBeanDefinition(classOf[PetFallback]).hasAnnotation(classOf[Fallback]))

      val pet = Mono.from(fallback.save("Dino", 10)).block()
      assertEquals("Dino", pet.name)
      assertEquals(10, pet.age)
    finally
      context.close()
