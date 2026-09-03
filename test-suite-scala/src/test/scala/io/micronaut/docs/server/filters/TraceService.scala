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
package io.micronaut.docs.server.filters

// tag::imports[]
import io.micronaut.http.HttpRequest
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
// end::imports[]

// tag::class[]
@Singleton
class TraceService:

  private val log = LoggerFactory.getLogger(classOf[TraceService])

  def trace(request: HttpRequest[?]): Publisher[java.lang.Boolean] =
    Mono.fromCallable(() =>
      log.debug("Tracing request: {}", request.getUri)
      // trace logic here, potentially performing I/O <1>
      java.lang.Boolean.TRUE
    ).subscribeOn(Schedulers.boundedElastic()) // <2>
      .flux()
// end::class[]
