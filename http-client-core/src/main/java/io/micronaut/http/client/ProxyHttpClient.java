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
package io.micronaut.http.client;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import org.reactivestreams.Publisher;

/**
 * Interface that allows proxying of HTTP requests in controllers and filters.
 *
 * @author graemerocher
 * @since 2.0.0
 */
public interface ProxyHttpClient {

    /**
     * Proxy the given request and emit the response. This method expects the full absolute URL to be included in the request.
     * If a relative URL is specified then the method will try to resolve the URI for the current server otherwise an exception will be thrown.
     *
     * @param request The request
     * @return A publisher that emits the response.
     */
    Publisher<MutableHttpResponse<?>> proxy(HttpRequest<?> request);
}
