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
package io.micronaut.docs.http.client.proxy

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class ProxyFilterSpec:

  @Test
  def proxyFilterRewritesTheRequest(): Unit =
    val server = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object]("spec.name" -> "ProxyFilterSpec").asJava
    )
    try
      val client = server.getApplicationContext.createBean(classOf[HttpClient], server.getURL)
      try
        val response = client.toBlocking.exchange("/proxy/get", classOf[String])
        assertEquals("YYY", response.header("X-My-Response-Header"))
        assertEquals("good XXX", response.body())
      finally
        client.stop()
    finally
      server.stop()

@Requires(property = "spec.name", value = "ProxyFilterSpec")
@Controller("/real")
class ProxyTargetController:

  @Get("/get")
  @Produces(Array(MediaType.TEXT_PLAIN))
  def index(headers: HttpHeaders): HttpResponse[String] =
    HttpResponse.ok("good " + headers.get("X-My-Request-Header"))
