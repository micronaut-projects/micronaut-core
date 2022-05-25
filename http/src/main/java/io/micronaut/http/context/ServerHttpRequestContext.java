/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.context;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import io.micronaut.http.HttpRequest;

/**
 * Http request propagation.
 *
 * @author Denis Stepanov
 * @since 3.6.0
 */
@Experimental
public final class ServerHttpRequestContext implements ThreadPropagatedContextElement<HttpRequest<?>> {

    private final HttpRequest<?> httpRequest;

    public ServerHttpRequestContext(HttpRequest<?> httpRequest) {
        this.httpRequest = httpRequest;
    }

    public HttpRequest<?> getHttpRequest() {
        return httpRequest;
    }

    @Nullable
    public static HttpRequest<Object> get() {
        return (HttpRequest<Object>) PropagatedContext.find()
                .flatMap(ctx -> ctx.find(ServerHttpRequestContext.class))
                .map(ServerHttpRequestContext::getHttpRequest)
                .orElse(null);
    }

    @Override
    public HttpRequest<?> updateThreadContext() {
        HttpRequest<?> prev = ServerRequestContext.currentRequest().orElse(null);
        ServerRequestContext.set(httpRequest);
        return prev;
    }

    @Override
    public void restoreThreadContext(HttpRequest<?> oldState) {
        ServerRequestContext.set(httpRequest);
    }
}
