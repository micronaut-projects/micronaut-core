/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.server.netty.ssl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpMessage;
import io.micronaut.http.server.netty.decoders.HttpRequestDecoder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.AttributeKey;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.Certificate;
import java.util.Optional;

/**
 * Provides Instances of {@link Certificate} to {@link io.micronaut.http.HttpRequest} if applicable.
 */
@Internal
public class SSLCertificateProviderHandler extends ChannelInboundHandlerAdapter {
    private static final String ID = SSLCertificateProviderHandler.class.getSimpleName();
    private static final String CERT_KEY = "javax.servlet.request.X509Certificate";
    private static final AttributeKey<Certificate> CERT_ATTRIBUTE = AttributeKey.newInstance(CERT_KEY);

    @Override
    public void channelRegistered(final ChannelHandlerContext ctx) throws Exception {
        ctx.pipeline().addAfter(HttpRequestDecoder.ID, ID, new AddCertificateToRequest());
        super.channelRegistered(ctx);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object source) throws Exception {
        if (isSuccessfulHandshakeEvent(source)) {
            Certificate cert = getCertificate(ctx.pipeline().get(SslHandler.class)).orElse(null);
            ctx.channel().attr(CERT_ATTRIBUTE).set(cert);
        }
        super.userEventTriggered(ctx, source);
    }

    private static boolean isSuccessfulHandshakeEvent(final Object source) {
        if (source instanceof SslHandshakeCompletionEvent) {
            SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) source;
            return event.isSuccess();
        }
        return false;
    }

    /**
     * Retrieves the current {@link Certificate} stored inside {@link SslHandler}.
     *
     * @param handler Session
     * @return Certificate, if found.
     */
    private static Optional<Certificate> getCertificate(final SslHandler handler) {
        try {
            return Optional.of(
                handler.engine().getSession().getPeerCertificates()[0]
            );
        } catch (SSLPeerUnverifiedException ex) {
            return Optional.empty();
        }
    }

    /**
     * Saves a given {@link Certificate} in a {@link HttpMessage}.
     */
    private static class AddCertificateToRequest extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
            if (msg instanceof HttpMessage) {
                HttpMessage<?> request = (HttpMessage<?>) msg;
                request.setAttribute(CERT_KEY, ctx.channel().attr(CERT_ATTRIBUTE).get());
                super.channelRead(ctx, msg);
                return;
            }
            throw new UnsupportedOperationException("Message must implement HttpMessage in order to set Certificate!");
        }
    }
}
