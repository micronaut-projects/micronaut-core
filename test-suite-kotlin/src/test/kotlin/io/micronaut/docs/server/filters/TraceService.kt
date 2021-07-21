/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.slf4j.LoggerFactory
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

// end::imports[]

// tag::class[]
@Singleton
class TraceService {

    private val LOG = LoggerFactory.getLogger(TraceService::class.java)

    internal fun trace(request: HttpRequest<*>): Flux<Boolean> {
        return Mono.fromCallable {
            // <1>
            LOG.debug("Tracing request: {}", request.uri)
            // trace logic here, potentially performing I/O <2>
            true
        }.subscribeOn(Schedulers.boundedElastic()) // <3>
            .flux()
    }
}
// end::class[]
