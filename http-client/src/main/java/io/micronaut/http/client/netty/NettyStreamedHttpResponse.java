/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.client.netty;

import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.netty.NettyHttpHeaders;

import java.util.Optional;

/**
 * Wrapper object for a {@link StreamedHttpResponse}.
 *
 * @param <B> The response body type
 * @author graemerocher
 * @since 1.0
 */
@Internal
class NettyStreamedHttpResponse<B> implements HttpResponse<B> {

    private final StreamedHttpResponse nettyResponse;
    private final HttpStatus status;
    private final NettyHttpHeaders headers;
    private B body;
    private MutableConvertibleValues<Object> attributes;

    /**
     * @param response The streamed Http response
     * @param httpStatus The Http status
     */
    NettyStreamedHttpResponse(StreamedHttpResponse response, HttpStatus httpStatus) {
        this.nettyResponse = response;
        this.status = httpStatus;
        this.headers = new NettyHttpHeaders(response.headers(), ConversionService.SHARED);
    }

    /**
     * @return The streamed Http response
     */
    public StreamedHttpResponse getNettyResponse() {
        return nettyResponse;
    }

    @Override
    public String reason() {
        return nettyResponse.status().reasonPhrase();
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        MutableConvertibleValues<Object> attributes = this.attributes;
        if (attributes == null) {
            synchronized (this) { // double check
                attributes = this.attributes;
                if (attributes == null) {
                    attributes = new MutableConvertibleValuesMap<>();
                    this.attributes = attributes;
                }
            }
        }
        return attributes;
    }

    /**
     * Sets the body.
     *
     * @param body The body
     */
    public void setBody(B body) {
        this.body = body;
    }

    @Override
    public Optional<B> getBody() {
        return Optional.ofNullable(body);
    }
}
