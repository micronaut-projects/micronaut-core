/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.netty.ssl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.Certificate;

/**
 * Adds the certificate to the decoded request.
 *
 * @author James Kleeh
 * @author Björn Heinrichs
 * @since 1.3.0
 */
@Internal
public class HttpRequestCertificateHandler extends ChannelInboundHandlerAdapter {
    private final SslHandler sslHandler;
    private Certificate certificate;

    public HttpRequestCertificateHandler(SslHandler sslHandler) {
        this.sslHandler = sslHandler;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpMessage<?> http) {
            if (certificate == null) {
                certificate = getCertificate(sslHandler);
                if (certificate == null) {
                    ctx.pipeline().remove(this);
                    super.channelRead(ctx, msg);
                    return;
                }
            }
            http.setAttribute(HttpAttributes.X509_CERTIFICATE, certificate);
        }
        super.channelRead(ctx, msg);
    }

    @Nullable
    private static Certificate getCertificate(final SslHandler handler) {
        try {
            return handler.engine().getSession().getPeerCertificates()[0];
        } catch (SSLPeerUnverifiedException ex) {
            return null;
        }
    }
}
