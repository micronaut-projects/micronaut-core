/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.MutableHttpHeaders;

/**
 * Delegates to Netty's {@link DefaultFullHttpResponse}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class NettyHttpResponse<B> implements HttpResponse<B> {
    final DefaultFullHttpResponse nettyResponse;
    final NettyHttpRequestHeaders headers;

    public NettyHttpResponse(DefaultFullHttpResponse nettyResponse, ConversionService conversionService) {
        this.nettyResponse = nettyResponse;
        this.headers = new NettyHttpRequestHeaders(nettyResponse.headers(), conversionService);
    }

    public NettyHttpResponse(ConversionService conversionService) {
        this.nettyResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        this.headers = new NettyHttpRequestHeaders(nettyResponse.headers(), conversionService);
    }

    @Override
    public MutableHttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public B getBody() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public HttpResponse setCharacterEncoding(CharSequence encoding) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public HttpResponse setStatus(HttpStatus status, CharSequence message) {
        nettyResponse.setStatus(new HttpResponseStatus(status.getCode(), message.toString()));
        return this;
    }
}
