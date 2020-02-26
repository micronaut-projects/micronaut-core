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

/**
 * @author Graeme Rocher
 * @since 1.0
 */
// tag::class[]
@Filter("/hello/**") // <1>
class TraceFilter implements HttpServerFilter { // <2>
    private final TraceService traceService

    TraceFilter(TraceService traceService) { // <3>
        this.traceService = traceService
    }
// end::class[]

    // tag::doFilter[]
    @Override
    Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        traceService.trace(request) // <1>
                           .switchMap({ aBoolean -> chain.proceed(request) }) // <2>
                           .doOnNext({ res -> // <3>
                               res.getHeaders().add("X-Trace-Enabled", "true")
                           })
    }
    // end::doFilter[]

// tag::endclass[]
}
// end::endclass[]
