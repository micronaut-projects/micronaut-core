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
package io.micronaut.docs.server.sse

// tag::imports[]
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.sse.Event
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
// end::imports[]

// tag::class[]
@Controller("/headlines")
class HeadlineController:

  @ExecuteOn(TaskExecutors.IO)
  @Get(produces = Array(MediaType.TEXT_EVENT_STREAM))
  def index(): Publisher[Event[Headline]] = // <1>
    val versions = Array("1.0", "2.0") // <2>
    Flux.generate(() => 0, (i, emitter) => { // <3>
      if i < versions.length then
        emitter.next( // <4>
          Event.of(Headline(s"Micronaut ${versions(i)} Released", "Come and get it"))
        )
      else
        emitter.complete() // <5>
      i + 1
    })
// end::class[]
