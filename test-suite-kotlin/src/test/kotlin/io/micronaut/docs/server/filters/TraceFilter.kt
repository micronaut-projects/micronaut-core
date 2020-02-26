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
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import org.reactivestreams.Publisher

// end::imports[]

// tag::class[]
@Filter("/hello/**") // <1>
class TraceFilter(// <2>
        private val traceService: TraceService)// <3>
    : HttpServerFilter {
    // end::class[]

    // tag::doFilter[]
    override fun doFilter(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        return traceService.trace(request) // <1>
                .switchMap { aBoolean -> chain.proceed(request) } // <2>
                .doOnNext { res ->
                    // <3>
                    res.headers.add("X-Trace-Enabled", "true")
                }
    }
    // end::doFilter[]

// tag::endclass[]
}
// end::endclass[]
