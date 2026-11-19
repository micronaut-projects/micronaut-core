/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.haproxy.HAProxyCommand;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyProxiedProtocol;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Reads the PROXY protocol (version 1 or 2) header at the start of a connection, records the
 * addresses it carries on the connection channel, and then removes itself together with the
 * decoder. A connection that does not start with a valid header, or does not send it in time,
 * is closed: falling back to the socket address would let a client that reaches the listener
 * directly choose whether its address is trusted.
 *
 * @since 5.3.0
 */
@Internal
public final class ProxyProtocolHandler extends ChannelInboundHandlerAdapter {
    /**
     * The name of the {@link HAProxyMessageDecoder} in the pipeline.
     */
    public static final String DECODER_NAME = "proxy-protocol-decoder";
    /**
     * The name of this handler in the pipeline.
     */
    public static final String HANDLER_NAME = "proxy-protocol";

    private static final AttributeKey<ProxiedAddresses> ADDRESSES = AttributeKey.valueOf("micronaut.server.proxy-protocol.addresses");
    private static final Logger LOG = LoggerFactory.getLogger(ProxyProtocolHandler.class);

    private final long timeoutMillis;
    @Nullable
    private ScheduledFuture<?> timeout;
    private boolean done;

    /**
     * @param timeoutMillis How long the peer may take to send the header, {@code <= 0} for no limit
     */
    public ProxyProtocolHandler(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Add the decoder and this handler at the end of the given pipeline. They must come before
     * any other handler that reads the connection, the TLS handler in particular.
     *
     * @param pipeline      The connection pipeline
     * @param timeoutMillis How long the peer may take to send the header, {@code <= 0} for no limit
     */
    public static void insert(ChannelPipeline pipeline, long timeoutMillis) {
        pipeline.addLast(DECODER_NAME, new HAProxyMessageDecoder());
        pipeline.addLast(HANDLER_NAME, new ProxyProtocolHandler(timeoutMillis));
    }

    /**
     * The addresses that the PROXY protocol header of the connection of the given channel
     * carries. The channel may be the connection channel or a stream channel of it.
     *
     * @param channel The channel
     * @return The addresses, or {@code null} if the connection did not send any (no PROXY
     * protocol, a {@code LOCAL} command or an unknown or non-IP protocol)
     */
    public static @Nullable ProxiedAddresses find(@Nullable Channel channel) {
        for (Channel c = channel; c != null; c = c.parent()) {
            if (c.hasAttr(ADDRESSES)) {
                return c.attr(ADDRESSES).get();
            }
        }
        return null;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        if (timeoutMillis > 0) {
            timeout = ctx.executor().schedule(() -> {
                if (!done) {
                    LOG.debug("Closing connection {}: no PROXY protocol header within {}ms", ctx.channel(), timeoutMillis);
                    ctx.close();
                }
            }, timeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        cancelTimeout();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (done) {
            ctx.fireChannelRead(msg);
            return;
        }
        if (!(msg instanceof HAProxyMessage message)) {
            // the decoder only passes bytes on once it has removed itself, after the header
            ReferenceCountUtil.release(msg);
            fail(ctx, null);
            return;
        }
        try {
            ProxiedAddresses addresses = addresses(message);
            if (addresses != null) {
                ctx.channel().attr(ADDRESSES).set(addresses);
            }
        } catch (RuntimeException e) {
            fail(ctx, e);
            return;
        } finally {
            message.release();
        }
        done = true;
        cancelTimeout();
        ChannelPipeline pipeline = ctx.pipeline();
        if (pipeline.get(DECODER_NAME) != null) {
            pipeline.remove(DECODER_NAME);
        }
        pipeline.remove(this);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (done) {
            ctx.fireExceptionCaught(cause);
        } else {
            // a malformed or missing header (HAProxyProtocolException), or anything else before it
            fail(ctx, cause);
        }
    }

    private void fail(ChannelHandlerContext ctx, @Nullable Throwable cause) {
        done = true;
        cancelTimeout();
        LOG.debug("Closing connection {}: invalid PROXY protocol header", ctx.channel(), cause);
        ctx.close();
    }

    private void cancelTimeout() {
        ScheduledFuture<?> t = timeout;
        if (t != null) {
            timeout = null;
            t.cancel(false);
        }
    }

    private static @Nullable ProxiedAddresses addresses(HAProxyMessage message) {
        // LOCAL: a connection of the proxy itself, e.g. a health check. UNKNOWN / UNSPEC, and
        // UNIX: no IP address. In all these cases the receiver must use the real connection
        // addresses (PROXY protocol specification, sections 2.1 and 2.2).
        if (message.command() != HAProxyCommand.PROXY) {
            return null;
        }
        HAProxyProxiedProtocol.AddressFamily family = message.proxiedProtocol().addressFamily();
        if (family != HAProxyProxiedProtocol.AddressFamily.AF_IPv4 && family != HAProxyProxiedProtocol.AddressFamily.AF_IPv6) {
            return null;
        }
        return new ProxiedAddresses(
            address(message.sourceAddress(), message.sourcePort()),
            address(message.destinationAddress(), message.destinationPort())
        );
    }

    private static InetSocketAddress address(String literal, int port) {
        // the decoder has validated the literal, so this parses it and never looks a name up
        try {
            return new InetSocketAddress(InetAddress.getByName(literal), port);
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("Invalid PROXY protocol address: " + literal, e);
        }
    }

    /**
     * The addresses of the original connection, as the proxy reports them.
     *
     * @param source      The address of the client that connected to the proxy
     * @param destination The address that the client connected to
     */
    @Internal
    public record ProxiedAddresses(InetSocketAddress source, InetSocketAddress destination) {
    }
}
