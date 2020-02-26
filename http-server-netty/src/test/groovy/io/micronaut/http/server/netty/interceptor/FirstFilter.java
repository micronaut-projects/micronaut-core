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
package io.micronaut.http.server.netty.interceptor;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.FilterChain;
import io.micronaut.http.filter.HttpFilter;
import org.reactivestreams.Publisher;
import org.spockframework.util.Assert;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Filter("/secure**")
public class FirstFilter implements HttpFilter {

    @Override
    public int getOrder() {
        return TestSecurityFilter.POSITION - 100;
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
        request.getAttributes().put("first", true);
        Assert.that(!request.getAttributes().contains("authenticated"));
        Publisher<? extends HttpResponse<?>> publisher = chain.proceed(request);
        return Publishers.then(publisher, httpResponse -> {
            httpResponse.getHeaders();
        });
    }
}
