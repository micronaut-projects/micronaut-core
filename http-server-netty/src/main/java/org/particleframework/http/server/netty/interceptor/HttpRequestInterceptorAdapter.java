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
package org.particleframework.http.server.netty.interceptor;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.particleframework.core.order.Ordered;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.interceptor.HttpRequestInterceptor;
import org.particleframework.http.server.netty.NettyHttpRequest;
import org.particleframework.http.server.netty.NettyHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * <p>An adapter for the {@link HttpRequestInterceptor} interface, allowing requests to be intercepted and optionally proceeded</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class HttpRequestInterceptorAdapter extends SimpleChannelInboundHandler<HttpRequest<?>> implements Ordered {
    private static final Logger LOG = LoggerFactory.getLogger(HttpRequestInterceptorAdapter.class);
    private final HttpRequestInterceptor adapted;

    public HttpRequestInterceptorAdapter(HttpRequestInterceptor adapted) {
        this.adapted = adapted;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest<?> msg) throws Exception {
        if (adapted.matches(msg)) {
            try {
                adapted.intercept(msg, new HttpRequestInterceptor.RequestContext() {
                    @Override
                    public void proceed(HttpRequest<?> request) {
                        ctx.fireChannelRead(request);
                    }

                    @Override
                    public <T> CompletableFuture<T> write(T object) {
                        CompletableFuture<T> future = new CompletableFuture<>();

                        ctx.writeAndFlush(object).addListener(f -> {
                                    if (f.isSuccess()) {
                                        future.complete(object);
                                    } else {
                                        future.completeExceptionally(f.cause());
                                    }
                                    Optional<NettyHttpResponse> res = NettyHttpResponse.get((NettyHttpRequest<?>) msg);
                                    res.ifPresent(response -> {
                                                if (response.getStatus().getCode() >= 300) {
                                                    ((ChannelFuture) f).channel().close();
                                                }
                                            }
                                    );
                                }
                        );
                        return future;
                    }
                });
            } catch (Exception e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error occurred invoking interceptor {}: {}", adapted, e.getMessage());
                }
                ctx.fireExceptionCaught(e);
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public int getOrder() {
        return adapted.getOrder();
    }
}
