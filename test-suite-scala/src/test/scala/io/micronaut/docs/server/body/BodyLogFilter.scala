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
package io.micronaut.docs.server.body

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.ServerHttpRequest
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.body.ByteBody
import io.micronaut.http.body.CloseableByteBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux

import java.util.Base64
// end::imports[]

@Requires(property = "spec.name", value = "BodyLogFilterSpec")
// tag::clazz[]
@ServerFilter(Array("/person"))
class BodyLogFilter:
  private val LOG: Logger = LoggerFactory.getLogger(classOf[BodyLogFilter])

  @RequestFilter
  def logBody(request: ServerHttpRequest[?]): Unit = // <2>
    val ourCopy: CloseableByteBody = // <4>
      request.byteBody()
        .split(ByteBody.SplitBackpressureMode.SLOWEST) // <3>
        .allowDiscard() // <5>
    try
      Flux.from(ourCopy.toByteArrayPublisher()) // <6>
        .onErrorComplete(classOf[ByteBody.BodyDiscardedException]) // <7>
        .subscribe(array => LOG.info("Received body: {}", Base64.getEncoder.encodeToString(array))) // <8>
    finally
      ourCopy.close()
// end::clazz[]
