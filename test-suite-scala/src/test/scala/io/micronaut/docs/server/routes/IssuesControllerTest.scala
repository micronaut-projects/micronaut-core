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
package io.micronaut.docs.server.routes

// tag::imports[]
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*
// end::imports[]

// tag::startclass[]
class IssuesControllerTest:

  private def withClient(test: HttpClient => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "IssuesControllerTest").asJava
    ) // <1>
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try test(client)
      finally client.close() // <2>
    finally server.close() // <2>
  // end::startclass[]

  // tag::normal[]
  @Test
  def testIssue(): Unit =
    withClient { client =>
      val body = client.toBlocking.retrieve("/issues/12") // <3>

      assertNotNull(body)
      assertEquals("Issue # 12!", body) // <4>
    }

  @Test
  def testIssueFromId(): Unit =
    withClient { client =>
      val body = client.toBlocking.retrieve("/issues/issue/13")

      assertNotNull(body)
      assertEquals("Issue # 13!", body) // <5>
    }

  @Test
  def testProgrammaticRoute(): Unit =
    withClient { client =>
      val body = client.toBlocking.retrieve("/issues/show/14")

      assertNotNull(body)
      assertEquals("Issue # 14!", body)
    }

  @Test
  def testShowWithInvalidInteger(): Unit =
    withClient { client =>
      val e = assertThrows(
        classOf[HttpClientResponseException],
        () => client.toBlocking.exchange("/issues/hello")
      )

      assertEquals(400, e.getStatus.getCode) // <6>
    }

  @Test
  def testIssueWithoutNumber(): Unit =
    withClient { client =>
      val e = assertThrows(
        classOf[HttpClientResponseException],
        () => client.toBlocking.exchange("/issues/")
      )

      assertEquals(404, e.getStatus.getCode) // <7>
    }
  // end::normal[]

  // tag::defaultvalue[]
  @Test
  def testDefaultIssue(): Unit =
    withClient { client =>
      val body = client.toBlocking.retrieve("/issues/default")

      assertNotNull(body)
      assertEquals("Issue # 0!", body) // <1>
    }

  @Test
  def testNotDefaultIssue(): Unit =
    withClient { client =>
      val body = client.toBlocking.retrieve("/issues/default/1")

      assertNotNull(body)
      assertEquals("Issue # 1!", body) // <2>
    }
  // end::defaultvalue[]

// tag::endclass[]
// end::endclass[]
