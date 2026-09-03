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
package io.micronaut.docs.server.form

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class PersonControllerTest:

  private def withFixture(test: (HttpClient, PersonController) => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "PersonControllerFormTest").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      val controller = server.getApplicationContext.getBean(classOf[PersonController])
      try test(client, controller)
      finally client.stop()
    finally
      server.stop()

  @Test
  def testSave(): Unit =
    withFixture { (client, controller) =>
      val payload = "firstName=Fred&lastName=Flintstone&age=45"
      val request = HttpRequest.POST("/people", payload)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
      invoke(client, request, controller)
    }

  @Test
  def saveWithArgsOptional(): Unit =
    withFixture { (client, controller) =>
      val payload = "firstName=Fred&lastName=Flintstone&age=45"
      val request = HttpRequest.POST("/people/saveWithArgsOptional", payload)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
      invoke(client, request, controller)
    }

  @Test
  def testSaveWithArgs(): Unit =
    withFixture { (client, controller) =>
      val payload = "firstName=Fred&lastName=Flintstone&age=45"
      val request = HttpRequest.POST("/people/saveWithArgs", payload)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
      invoke(client, request, controller)
    }

  private def invoke(client: HttpClient, request: HttpRequest[?], controller: PersonController): Unit =
    assertDoesNotThrow(() => client.toBlocking.exchange(request))
    val person = controller.inMemoryDatastore.get("Fred")
    assertNotNull(person)
    assertEquals("Fred", person.firstName)
    assertEquals("Flintstone", person.lastName)
    assertEquals(45, person.age)
    controller.inMemoryDatastore.clear()
