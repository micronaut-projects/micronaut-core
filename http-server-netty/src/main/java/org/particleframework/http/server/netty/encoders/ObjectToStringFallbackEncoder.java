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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.*;
import org.particleframework.core.order.Ordered;
import org.particleframework.http.server.netty.NettyHttpRequest;
import org.particleframework.http.server.netty.NettyHttpResponse;
import org.particleframework.http.server.netty.handler.ChannelHandlerFactory;
import org.particleframework.http.sse.Event;

import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * An encoder that falls back to encoding the object as a String
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ObjectToStringFallbackEncoder extends MessageToMessageEncoder<Object> implements Ordered {

    public static final int ORDER = Ordered.LOWEST_PRECEDENCE;
    private final ChannelHandlerFactory.NettyHttpRequestProvider request;

    public ObjectToStringFallbackEncoder(ChannelHandlerFactory.NettyHttpRequestProvider provider) {
        this.request = provider;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return  !(msg instanceof HttpContent) && !(msg instanceof ByteBuf) && !(msg instanceof Event) && !(msg instanceof HttpObject);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        NettyHttpResponse res = NettyHttpResponse.getOrCreate(request.get());
        String string = msg.toString();
        ByteBuf content = Unpooled.copiedBuffer(string, StandardCharsets.UTF_8);
        if(msg instanceof Event) {
            Event event = (Event) msg;
            out.add(Event.of(event, content));
        }
        else {

            res = res.replace(content);
            FullHttpResponse httpResponse = res.getNativeResponse();
            if(!HttpUtil.isTransferEncodingChunked(httpResponse)) {
                httpResponse
                        .headers()
                        .add(HttpHeaderNames.CONTENT_LENGTH, string.length());
            }
            out.add(httpResponse);
        }
    }

    @Singleton
    public static class ObjectToStringFallbackEncoderFactory implements ChannelHandlerFactory, Ordered {
        @Override
        public int getOrder() {
            return ORDER;
        }

        @Override
        public ChannelHandler build(NettyHttpRequestProvider provider) {
            return new ObjectToStringFallbackEncoder(provider);
        }
    }
}
