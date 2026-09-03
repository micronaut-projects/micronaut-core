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
package io.micronaut.docs.http.server.secondary

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Named
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@MicronautTest
@Property(name = "secondary.enabled", value = StringUtils.TRUE)
@Property(name = "micronaut.http.client.ssl.insecure-trust-all-certificates", value = StringUtils.TRUE)
// tag::inject[]
class SecondaryServerTest(
    @Client(path = "/", id = SecondaryNettyServer.SERVER_ID) httpClient: HttpClient, // <1>
    @Named(SecondaryNettyServer.SERVER_ID) embeddedServer: EmbeddedServer // <2>
):
  // end::inject[]

  @Test
  def testCallSecondaryServer(): Unit =
    val result = httpClient.toBlocking().retrieve(
      HttpRequest.GET[Any](embeddedServer.getURI.toString + "/test/secondary/server")
    )
    assertTrue(result.endsWith(embeddedServer.getPort.toString))
    assertTrue(embeddedServer.getScheme.equalsIgnoreCase("https"))

@Requires(property = "secondary.enabled", value = StringUtils.TRUE)
@Controller("/test/secondary/server")
class TestController:
  @Get
  def hello(request: HttpRequest[?]): String =
    "Hello from: " + request.getServerAddress.getPort
