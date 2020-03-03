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
package io.micronaut.discovery.consul

import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.core.util.Toggleable
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import org.reactivestreams.Publisher

/**
 * @author graemerocher
 * @since 1.0
 */
@Filter('/v1/**')
@Requires('consul.client.asl-token')
class MockConsulAuth implements HttpServerFilter, Toggleable{

    final Optional<String> token

    MockConsulAuth(@Value('${consul.client.asl-token}') Optional<String> token) {
        this.token = token
    }

    @Override
    Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        def token = request.headers.get('X-Consul-Token', String)
        if(this.token.isPresent() && (!token.isPresent() || token.get() != this.token.get())) {
            return Publishers.just(HttpResponse.status(HttpStatus.FORBIDDEN))
        }
        else {
            return chain.proceed(request)
        }
    }
}
