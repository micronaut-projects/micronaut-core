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
package org.particleframework.http.client.rxjava2;

import com.typesafe.netty.http.StreamedHttpResponse;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.value.MutableConvertibleValues;
import org.particleframework.core.convert.value.MutableConvertibleValuesMap;
import org.particleframework.http.HttpHeaders;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.netty.NettyHttpHeaders;

import java.util.Optional;

/**
 * Wrapper object for a {@link StreamedHttpResponse}
 *
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

    NettyStreamedHttpResponse(StreamedHttpResponse response) {
        this.nettyResponse = response;
        this.status = HttpStatus.valueOf(response.status().code());
        this.headers = new NettyHttpHeaders(response.headers(), ConversionService.SHARED);
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
                    this.attributes = attributes = new MutableConvertibleValuesMap<>();
                }
            }
        }
        return attributes;
    }

    public void setBody(B body) {
        this.body = body;
    }

    @Override
    public Optional<B> getBody() {
        return Optional.ofNullable(body);
    }
}
