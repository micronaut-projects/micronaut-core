package org.particleframework.http.server.netty.encoders;
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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.particleframework.core.naming.Named;
import org.particleframework.core.order.Ordered;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.server.netty.NettyHttpRequest;
import org.particleframework.http.server.netty.NettyHttpResponse;

import javax.inject.Singleton;
import java.util.List;

/**
 * Encodes an {@link HttpResponse} to it's body.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ChannelHandler.Sharable
@Singleton
public class HttpBodyEncoder extends MessageToMessageEncoder<HttpResponse> implements Ordered, Named {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 1000;
    public static final String NAME = "http-body-encoder";

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof NettyHttpResponse;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpResponse msg, List<Object> out) throws Exception {
        NettyHttpResponse res = (NettyHttpResponse) msg;
        NettyHttpResponse.set(NettyHttpRequest.current(ctx), res);

        Object body = res.getBody();
        if(body != null) {
            out.add(body);
        }
        else {
            out.add(res);
        }

    }

    @Override
    public String getName() {
        return NAME;
    }
}
