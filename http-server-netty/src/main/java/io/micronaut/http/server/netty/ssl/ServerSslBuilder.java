package io.micronaut.http.server.netty.ssl;

import io.micronaut.http.ssl.ServerSslConfiguration;
import io.netty.handler.ssl.SslContext;

import java.util.Optional;

public interface ServerSslBuilder {
    ServerSslConfiguration getSslConfiguration();

    @SuppressWarnings("Duplicates")
    Optional<SslContext> build();
}
