/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.netty.interceptor;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;
import org.spockframework.util.Assert;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Filter("/secure**")
public class TestSecurityFilter implements HttpServerFilter {

    public static final int POSITION = 0;

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        Assert.that(request.getAttributes().contains("first"));
        Assert.that(!request.getAttributes().contains("second"));
        if(request.getParameters().get("username") == null) {
            return Publishers.just(HttpResponse.status(HttpStatus.FORBIDDEN));
        }
        else {
            request.getAttributes().put("authenticated", true);
            return Publishers.then(
                    chain.proceed(request),
                    mutableHttpResponse -> mutableHttpResponse.header("X-Test", "Foo " + request.getAttributes().get("SomeServiceValue", String.class, "none"))
            );
        }
    }
}
