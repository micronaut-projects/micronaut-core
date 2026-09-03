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
package io.micronaut.docs.streaming

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest.GET
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class HeadlineControllerSpec:

  // tag::streamingClient[]
  @Test
  def testClientAnnotationStreaming(): Unit =
    val embeddedServer = ApplicationContext.run(classOf[EmbeddedServer])
    try
      val headlineClient = embeddedServer
        .getApplicationContext
        .getBean(classOf[HeadlineClient]) // <1>

      val firstHeadline = Mono.from(headlineClient.streamHeadlines()) // <2>

      val headline = firstHeadline.block() // <3>

      assertNotNull(headline)
      assertTrue(headline.text.startsWith("Latest Headline"))
    finally
      embeddedServer.close()
  // end::streamingClient[]

  @Test
  def testStreamingClient(): Unit =
    val embeddedServer = ApplicationContext.run(classOf[EmbeddedServer])
    val client = embeddedServer.getApplicationContext.createBean(
      classOf[StreamingHttpClient],
      embeddedServer.getURL
    )
    try
      // tag::streaming[]
      val headlineStream = Flux.from(client.jsonStream(
        GET("/streaming/headlines"),
        classOf[Headline]
      )) // <1>
      val future = CompletableFuture[Headline]() // <2>
      headlineStream.subscribe(new Subscriber[Headline]:
        override def onSubscribe(s: Subscription): Unit =
          s.request(1) // <3>

        override def onNext(headline: Headline): Unit =
          println(s"Received Headline = ${headline.text}")
          future.complete(headline) // <4>

        override def onError(t: Throwable): Unit =
          future.completeExceptionally(t) // <5>

        override def onComplete(): Unit =
          // no-op // <6>
          ()
      )

      // end::streaming[]
      val headline = future.get(3, TimeUnit.SECONDS)
      assertTrue(headline.text.startsWith("Latest Headline"))
    finally
      client.close()
      embeddedServer.close()
