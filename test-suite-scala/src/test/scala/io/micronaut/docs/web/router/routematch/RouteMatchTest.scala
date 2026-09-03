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
package io.micronaut.docs.web.router.routematch

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.RouteAttributes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class RouteMatchTest:

  @Test
  def testRouteMatchRetrieval(): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "RouteMatchSpec").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try
        assertEquals(
          "text/plain",
          client.toBlocking.retrieve(HttpRequest.GET[Any]("/routeMatch").accept(MediaType.TEXT_PLAIN_TYPE))
        )
      finally client.stop()
    finally server.stop()

@Requires(property = "spec.name", value = "RouteMatchSpec")
@Controller
class RouteMatchController:

  @Produces(Array(MediaType.TEXT_PLAIN))
  @Get("/routeMatch")
  // tag::routematch[]
  def index(request: HttpRequest[?]): String | Null =
    val routeMatch = RouteAttributes.getRouteMatch(request)
      .orElse(null)
  // end::routematch[]
    if routeMatch != null then
      routeMatch.getRouteInfo.getProduces.stream().map(_.toString).findFirst().orElse(null)
    else null
