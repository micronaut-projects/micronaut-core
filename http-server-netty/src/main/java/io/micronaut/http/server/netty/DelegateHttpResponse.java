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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * Delegate for HTTP Response.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DelegateHttpResponse extends DelegateHttpMessage implements HttpResponse {

    protected final HttpResponse response;

    /**
     * @param response The {@link HttpResponse}
     */
    DelegateHttpResponse(HttpResponse response) {
        super(response);
        this.response = response;
    }

    @Override
    public HttpResponse setStatus(HttpResponseStatus status) {
        response.setStatus(status);
        return this;
    }

    @Override
    @Deprecated
    public HttpResponseStatus getStatus() {
        return response.status();
    }

    @Override
    public HttpResponseStatus status() {
        return response.status();
    }

    @Override
    public HttpResponse setProtocolVersion(HttpVersion version) {
        super.setProtocolVersion(version);
        return this;
    }
}
