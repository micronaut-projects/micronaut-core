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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * Implementation of {@link HttpResponseFactory} for Netty.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class NettyHttpResponseFactory implements HttpResponseFactory {

    @Override
    public <T> MutableHttpResponse<T> ok(T body) {
        return new NettyMutableHttpResponse<>(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, null, body, ConversionService.SHARED);
    }

    @Override
    public <T> MutableHttpResponse<T> status(HttpStatus status, T body) {
        return ok(body).status(status);
    }

    @Override
    public <T> MutableHttpResponse<T> status(HttpStatus status, String reason) {
        HttpResponseStatus nettyStatus;
        if (reason == null) {
            nettyStatus = HttpResponseStatus.valueOf(status.getCode());
        } else {
            nettyStatus = HttpResponseStatus.valueOf(status.getCode(), reason);
        }
        return new NettyMutableHttpResponse<>(HttpVersion.HTTP_1_1, nettyStatus, ConversionService.SHARED);
    }

    @Override
    public <T> MutableHttpResponse<T> status(int status, String reason) {
        HttpResponseStatus nettyStatus;
        if (reason == null) {
            nettyStatus = HttpResponseStatus.valueOf(status);
        } else {
            nettyStatus = HttpResponseStatus.valueOf(status, reason);
        }
        return new NettyMutableHttpResponse<>(HttpVersion.HTTP_1_1, nettyStatus, ConversionService.SHARED);
    }

}
