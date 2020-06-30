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
package io.micronaut.http.filter;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import org.reactivestreams.Publisher;

/**
 * <p>A non-blocking and thread-safe filter chain. Consumers should call {@link #proceed(HttpRequest)} to continue with
 * the request or return an alternative {@link io.micronaut.http.HttpResponse} {@link Publisher}.</p>
 * <p>
 * <p>The context instance itself can be passed to other threads as necessary if blocking operations are required to
 * implement the {@link HttpFilter}</p>
 */
public interface ServerFilterChain extends FilterChain {

    /**
     * Proceed to the next interceptor or final request invocation.
     *
     * @param request The current request
     * @return A {@link Publisher} for the Http response
     */
    Publisher<MutableHttpResponse<?>> proceed(HttpRequest<?> request);
}
