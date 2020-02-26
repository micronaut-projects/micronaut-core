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
package io.micronaut.discovery.eureka

import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.*
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import org.reactivestreams.Publisher

/**
 * @author graemerocher
 * @since 1.0
 */
@Filter('/eureka/**')
@Requires(property = 'test.eureka.userinfo')
class MockEurekaAuth implements HttpServerFilter {
    final String userInfo

    MockEurekaAuth(@Value('${test.eureka.userinfo}') String userInfo) {
        this.userInfo = userInfo
    }

    @Override
    Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        Optional<String> authToken = request.getHeaders().get(HttpHeaders.AUTHORIZATION, String)
        if(authToken.isPresent()) {
            String token = authToken.get().substring(HttpHeaderValues.AUTHORIZATION_PREFIX_BASIC.length())
            String val = new String(token.decodeBase64())
            if(val.contains(userInfo)) {
                return chain.proceed(request)
            }
        }
        Publishers.just(HttpResponse.status(HttpStatus.FORBIDDEN))
    }
}
