/*
 * Copyright 2018 original authors
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
package org.particleframework.http.client;

import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.reactivestreams.Publisher;

import java.util.Map;

/**
 * Extended version of the {@link HttpClient} that supports streaming responses
 */
public interface StreamingHttpClient extends HttpClient {

    /**
     * Perform an HTTP request and receive data as a stream of JSON objects as they become available
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    <I> Publisher<Map<String, Object>> jsonStream(HttpRequest<I> request);
}
