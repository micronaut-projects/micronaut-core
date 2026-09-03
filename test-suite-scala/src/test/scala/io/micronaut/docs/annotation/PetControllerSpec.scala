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
package io.micronaut.docs.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.validation.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

import scala.jdk.CollectionConverters.*

class PetControllerSpec:

  private def withClient(test: PetClient => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "PetControllerSpec").asJava
    )
    try
      val client = server.getApplicationContext.getBean(classOf[PetClient])
      test(client)
    finally server.stop()

  @Test
  def testPostPet(): Unit =
    withClient { client =>
      // tag::post[]
      val pet = Mono.from(client.save("Dino", 10)).block()

      assertEquals("Dino", pet.name)
      assertEquals(10, pet.age)
      // end::post[]
    }

  @Test
  def testPostPetValidation(): Unit =
    withClient { client =>
      val e = assertThrows(
        classOf[ConstraintViolationException],
        () => Mono.from(client.save("Fred", -1)).block()
      )
      assertEquals("save.age: must be greater than or equal to 1", e.getMessage)
    }
