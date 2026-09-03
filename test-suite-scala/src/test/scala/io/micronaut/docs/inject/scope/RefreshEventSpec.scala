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
package io.micronaut.docs.inject.scope

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.context.scope.Refreshable
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

class RefreshEventSpec:

  @Test
  def publishingARefreshEventDestroysBeanWithRefreshableScope(): Unit =
    val embeddedServer = ApplicationContext.run(
      classOf[EmbeddedServer],
      Map[String, Object](
        "spec.name" -> "RefreshEventSpec",
        "spec.lang" -> "scala"
      ).asJava,
      Environment.TEST
    )
    val client = embeddedServer.getApplicationContext.createBean(classOf[HttpClient], embeddedServer.getURL)
    try
      val firstResponse = fetchForecast(client)
      assertTrue(firstResponse.contains("{\"forecast\":\"Scattered Clouds"))

      val secondResponse = fetchForecast(client)
      assertEquals(firstResponse, secondResponse)

      assertEquals("{\"msg\":\"OK\"}", evictForecast(client))

      val thirdResponse = waitForForecastChange(client, secondResponse)
      assertNotEquals(thirdResponse, secondResponse)
      assertTrue(thirdResponse.contains("\"forecast\":\"Scattered Clouds"))
    finally
      client.close()
      embeddedServer.close()

  private def fetchForecast(client: HttpClient): String =
    client.toBlocking.retrieve(HttpRequest.GET("/weather/forecast"))

  private def evictForecast(client: HttpClient): String =
    client.toBlocking.retrieve(HttpRequest.POST("/weather/evict", new LinkedHashMap[String, String]()))

  private def waitForForecastChange(client: HttpClient, previous: String): String =
    var response = fetchForecast(client)
    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
    while response == previous && System.nanoTime() < deadline do
      Thread.sleep(100)
      response = fetchForecast(client)
    response

// tag::weatherService[]
@Refreshable // <1>
class WeatherService:
  private var forecast: String = uninitialized

  @PostConstruct
  def init(): Unit =
    forecast = "Scattered Clouds " + SimpleDateFormat("dd/MMM/yy HH:mm:ss.SSS").format(Date()) // <2>

  def latestForecast(): String = forecast
// end::weatherService[]

@Requires(property = "spec.name", value = "RefreshEventSpec")
@Requires(property = "spec.lang", value = "scala")
@Controller("/weather")
class WeatherController(
    private val weatherService: WeatherService,
    private val applicationContext: ApplicationContext
):

  @Get("/forecast")
  def index(): HttpResponse[java.util.Map[String, String]] =
    val body = LinkedHashMap[String, String](1)
    body.put("forecast", weatherService.latestForecast())
    HttpResponse.ok(body)

  @Post("/evict")
  def evict(): HttpResponse[java.util.Map[String, String]] =
    // tag::publishEvent[]
    applicationContext.publishEvent(RefreshEvent())
    // end::publishEvent[]
    val body = LinkedHashMap[String, String](1)
    body.put("msg", "OK")
    HttpResponse.ok(body)
