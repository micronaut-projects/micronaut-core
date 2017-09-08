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
import org.particleframework.core.convert.DefaultConversionService;
import org.particleframework.http.HttpResponseFactory;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.MutableHttpResponse;

/**
 * Implementation of {@link HttpResponseFactory} for Netty
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyHttpResponseFactory implements HttpResponseFactory {
    @Override
    public <T> MutableHttpResponse<T> ok(T body) {
        MutableHttpResponse<T> ok = new NettyHttpResponse<>(DefaultConversionService.SHARED_INSTANCE);

        return body != null ? ok.setBody(body) : ok;
    }

    @Override
    public MutableHttpResponse status(HttpStatus status, String reason) {
        HttpResponseStatus nettyStatus;
        if(reason == null) {
            nettyStatus = HttpResponseStatus.valueOf(status.getCode());
        }
        else {
            nettyStatus = new HttpResponseStatus(status.getCode(), reason);
        }

        DefaultFullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, nettyStatus);
        return new NettyHttpResponse(fullHttpResponse, DefaultConversionService.SHARED_INSTANCE);
    }
}
