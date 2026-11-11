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
package io.micronaut.http.client.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;

/**
 * Calls a {@link HostnameVerifier} when the TLS handshake completes, and fails the handshake when it rejects the
 * host. Sits right after the {@link io.netty.handler.ssl.SslHandler}, so the handlers after it, including ALPN, only
 * see the verified outcome.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
final class HostnameVerifierHandler extends ChannelInboundHandlerAdapter {
    private final HostnameVerifier hostnameVerifier;
    private final SSLEngine engine;

    HostnameVerifierHandler(HostnameVerifier hostnameVerifier, SSLEngine engine) {
        this.hostnameVerifier = hostnameVerifier;
        this.engine = engine;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent event && event.isSuccess()) {
            SSLPeerUnverifiedException failure = verify();
            if (failure != null) {
                ctx.fireUserEventTriggered(new SslHandshakeCompletionEvent(failure));
                ctx.fireExceptionCaught(failure);
                ctx.close();
            } else {
                ctx.fireUserEventTriggered(evt);
            }
            ctx.pipeline().remove(this);
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    private @Nullable SSLPeerUnverifiedException verify() {
        String host = engine.getPeerHost();
        try {
            if (hostnameVerifier.verify(host, engine.getSession())) {
                return null;
            }
            return new SSLPeerUnverifiedException("Host name verification failed for " + host);
        } catch (RuntimeException e) {
            SSLPeerUnverifiedException failure = new SSLPeerUnverifiedException("Host name verification failed for " + host + ": " + e.getMessage());
            failure.initCause(e);
            return failure;
        }
    }
}
