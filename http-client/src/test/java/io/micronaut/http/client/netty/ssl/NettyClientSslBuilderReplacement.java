package io.micronaut.http.client.netty.ssl;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.env.Environment;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.ssl.SslConfiguration;
import io.netty.handler.ssl.SslContextBuilder;
import jakarta.inject.Singleton;

@Requires(env = Environment.TEST)
@Singleton
@BootstrapContextCompatible
@Secondary
@Replaces(NettyClientSslBuilder.class)
class NettyClientSslBuilderReplacement extends NettyClientSslBuilder {
    NettyClientSslBuilderReplacement(ResourceResolver resourceResolver) {
        super(resourceResolver);
    }

    @Override
    protected SslContextBuilder createSslContextBuilder(SslConfiguration ssl, HttpVersionSelection versionSelection) {
        SslContextBuilder builder = super.createSslContextBuilder(ssl, versionSelection);
        builder.endpointIdentificationAlgorithm(null);
        return builder;
    }
}
