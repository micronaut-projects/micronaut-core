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

import io.micronaut.http.annotation.Filter;
import io.reactivex.Flowable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Filter("/secure**")
public class TestReactiveFilter implements HttpServerFilter{

    @Override
    public int getOrder() {
        return TestSecurityFilter.POSITION - 10;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        SomeService someService = new SomeService();
        return someService
                    .getSomething()
                    .switchMap(s -> {
                        request.getAttributes().put("SomeServiceValue", s);
                        return chain.proceed(request);
                    });
    }

    class SomeService {
        Flowable<String> getSomething() {
            return Flowable.just("Test");
        }
    }
}
