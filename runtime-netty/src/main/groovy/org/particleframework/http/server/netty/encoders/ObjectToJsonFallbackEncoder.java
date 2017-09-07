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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.*;
import org.particleframework.core.order.Ordered;
import org.particleframework.http.server.netty.NettyHttpResponse;

import javax.inject.Singleton;
import java.util.List;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@ChannelHandler.Sharable
@Singleton
public class ObjectToJsonFallbackEncoder extends MessageToMessageEncoder<Object> implements Ordered {

    public static final int ORDER = ObjectToStringFallbackEncoder.ORDER - 50;

    private final ObjectMapper objectMapper;

    public ObjectToJsonFallbackEncoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return !(msg instanceof CharSequence) && !(msg instanceof HttpResponse);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        NettyHttpResponse res = ctx
                                    .channel()
                                    .attr(NettyHttpResponse.KEY)
                                    .get();


        DefaultFullHttpResponse httpResponse = res != null ? res.getNativeResponse() : new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        byte[] bytes = objectMapper.writeValueAsBytes(msg);
        ByteBuf content = Unpooled.copiedBuffer(bytes);
        int len = bytes.length;
        httpResponse
                .headers()
                .add(HttpHeaderNames.CONTENT_LENGTH, len);
        out.add(httpResponse.replace(content));
    }

}
