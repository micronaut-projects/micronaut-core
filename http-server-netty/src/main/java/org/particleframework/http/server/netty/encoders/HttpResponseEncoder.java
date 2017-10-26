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
package org.particleframework.http.server.netty.encoders;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.FullHttpResponse;
import org.particleframework.core.order.Ordered;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.server.netty.NettyHttpRequest;
import org.particleframework.http.server.netty.NettyHttpResponse;
import org.particleframework.http.server.netty.handler.ChannelHandlerFactory;

import javax.inject.Singleton;
import java.util.List;

/**
 * Encodes an {@link HttpResponse} to a {@link io.netty.handler.codec.http.FullHttpResponse}
 *
 * @author Graeme Rocher
 * @since 1.0
 */

public class HttpResponseEncoder extends MessageToMessageEncoder<HttpResponse> implements Ordered {

    private final ChannelHandlerFactory.NettyHttpRequestProvider request;

    public HttpResponseEncoder(ChannelHandlerFactory.NettyHttpRequestProvider request) {
        this.request = request;
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof NettyHttpResponse;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpResponse msg, List<Object> out) throws Exception {
        NettyHttpResponse res = (NettyHttpResponse) msg;
        FullHttpResponse nativeResponse = res.getNativeResponse();
        NettyHttpResponse.set(request.get(), res);

        Object body = res.getBody();
        if(body != null) {
            out.add(body);
        }
        else {
            out.add(nativeResponse);
        }

    }

    @Singleton
    public static class HttpResponseEncoderFactory implements ChannelHandlerFactory {
        @Override
        public ChannelHandler build(NettyHttpRequestProvider provider) {
            return new HttpResponseEncoder(provider);
        }
    }
}
