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
package io.micronaut.docs.server.routing

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class RouteConditionControllerSpec:

  private def withClient(test: HttpClient => Unit): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "RouteConditionControllerSpec").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try test(client)
      finally client.stop()
    finally server.stop()

  @Test
  def testRouteConditionV1(): Unit =
    withClient: client =>
      val response = client.toBlocking.retrieve(HttpRequest.GET[Any]("/api/hello"))
      assertEquals("Hello v1", response)

  @Test
  def testRouteConditionV2(): Unit =
    withClient: client =>
      val response = client.toBlocking.retrieve(HttpRequest.GET[Any]("/api/hello?v=2"))
      assertEquals("Hello v2", response)

  @Test
  def testRouteConditionFallsBackToV1ForUnmatchedVersion(): Unit =
    withClient: client =>
      val response = client.toBlocking.retrieve(HttpRequest.GET[Any]("/api/hello?v=3"))
      assertEquals("Hello v1", response)
