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
package org.particleframework.configuration.jackson.server.http.encoders;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpUtil;
import org.particleframework.core.order.Ordered;
import org.particleframework.http.HttpMessage;
import org.particleframework.http.MediaType;
import org.particleframework.http.MutableHttpHeaders;
import org.particleframework.http.annotation.Produces;
import org.particleframework.http.server.netty.NettyHttpRequest;
import org.particleframework.http.server.netty.NettyHttpResponse;
import org.particleframework.http.server.netty.encoders.ObjectToStringFallbackEncoder;
import org.particleframework.http.sse.Event;

import javax.inject.Singleton;
import java.util.List;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@ChannelHandler.Sharable
@Singleton
public class ObjectToJsonFallbackEncoder extends MessageToMessageEncoder<Object> implements Ordered {

    public static final int ORDER = ObjectToStringFallbackEncoder.OBJECT_FALLBACK_ORDER_START + 50;

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
        return !(msg instanceof HttpMessage) && !(msg instanceof CharSequence) && !(msg instanceof ByteBuf) && !(msg instanceof HttpContent) && !(msg instanceof HttpObject) ;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        NettyHttpResponse res = NettyHttpResponse.getOrCreate(NettyHttpRequest.current(ctx));

        if(msg instanceof Event) {
            Event event = (Event) msg;
            byte[] bytes = objectMapper.writeValueAsBytes(event.getData());
            out.add(Event.of((Event)msg, Unpooled.copiedBuffer(bytes)));
        }
        else {
            Produces produces = msg.getClass().getAnnotation(Produces.class);
            byte[] bytes = objectMapper.writeValueAsBytes(msg);
            ByteBuf content = Unpooled.copiedBuffer(bytes);
            int len = bytes.length;
            MutableHttpHeaders headers = res.getHeaders();
            if(!HttpUtil.isTransferEncodingChunked(res.getNativeResponse())) {
                headers
                        .add(HttpHeaderNames.CONTENT_LENGTH, len);
            }
            if(headers.get(HttpHeaderNames.CONTENT_TYPE) == null) {
                if(produces != null && produces.value().length > 0) {
                    headers.add(HttpHeaderNames.CONTENT_TYPE, MediaType.of(produces.value()[0]) );
                }
                else {
                    headers.add(HttpHeaderNames.CONTENT_TYPE, MediaType.APPLICATION_JSON_TYPE );
                }
            }
            out.add(res.replace(content));
        }
    }

}
