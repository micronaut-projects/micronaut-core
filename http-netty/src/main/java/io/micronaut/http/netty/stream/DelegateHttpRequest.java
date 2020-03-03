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
package io.micronaut.http.netty.stream;

import io.micronaut.core.annotation.Internal;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

/**
 * Delegate for HTTP Request.
 *
 * @author jroper
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DelegateHttpRequest extends DelegateHttpMessage implements HttpRequest {

    protected final HttpRequest request;

    /**
     * @param request The Http request
     */
    DelegateHttpRequest(HttpRequest request) {
        super(request);
        this.request = request;
    }

    @Override
    public HttpRequest setMethod(HttpMethod method) {
        request.setMethod(method);
        return this;
    }

    @Override
    public HttpRequest setUri(String uri) {
        request.setUri(uri);
        return this;
    }

    @Override
    @Deprecated
    public HttpMethod getMethod() {
        return request.method();
    }

    @Override
    public HttpMethod method() {
        return request.method();
    }

    @Override
    @Deprecated
    public String getUri() {
        return request.uri();
    }

    @Override
    public String uri() {
        return request.uri();
    }

    @Override
    public HttpRequest setProtocolVersion(HttpVersion version) {
        super.setProtocolVersion(version);
        return this;
    }
}
