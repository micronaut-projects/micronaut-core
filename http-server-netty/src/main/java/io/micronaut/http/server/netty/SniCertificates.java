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
package io.micronaut.http.server.netty;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.SslContextAutoLoader;
import io.micronaut.http.netty.SslContextHolder;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.netty.channel.Channel;
import io.netty.handler.codec.quic.QuicSslContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The host specific certificates of a listener, selected by the TLS server name (SNI): one
 * {@link SslContextAutoLoader} per certificate provider, shared by the host names it serves.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class SniCertificates {
    /**
     * The loaders by host name or wildcard pattern.
     */
    private final AtomicReference<Map<String, SslContextAutoLoader>> byHostName = new AtomicReference<>(Map.of());
    /**
     * The loaders by certificate provider name. Kept across updates, so that a host keeps its
     * last working context until a new one is built.
     */
    private Map<String, SslContextAutoLoader> byProvider = Map.of();
    private final Supplier<SslContextAutoLoader> loaderFactory;

    /**
     * @param loaderFactory Creates a loader for a host specific certificate
     */
    SniCertificates(Supplier<SslContextAutoLoader> loaderFactory) {
        this.loaderFactory = loaderFactory;
    }

    /**
     * Fail when a listener combines certificates by server name with a provided SSL context: the
     * host contexts could not trust the same clients as the provided one.
     *
     * @param listener The listener configuration
     * @param ssl      The server SSL configuration, or {@code null}
     */
    static void validate(NettyHttpServerConfiguration.NettyListenerConfiguration listener, @Nullable ServerSslConfiguration ssl) {
        if (ssl != null && !listener.getSniKeyNames().isEmpty() && (ssl.getSslContext() != null || ssl.getContextName() != null)) {
            throw new ConfigurationException("Listener " + listener.getName() + " configures sni-key-names, which cannot be combined with a provided SSLContext (context-name)");
        }
    }

    /**
     * @return Whether no host specific certificate is configured
     */
    boolean isEmpty() {
        return current().isEmpty();
    }

    /**
     * @return A selector that reads the loaders current at handshake time, so that handshakes
     * pending during an update use the new loaders
     */
    SniSelector selector() {
        return new SniSelector(this::current);
    }

    private Map<String, SslContextAutoLoader> current() {
        return Objects.requireNonNull(byHostName.get());
    }

    /**
     * Load the certificates for the given host names. An existing loader of a provider is loaded
     * again rather than replaced: it keeps serving its current context until the new one is
     * built. Loaders of providers that are no longer used are cleared.
     *
     * @param sniKeyNames The certificate provider names by host name
     * @param trustName   The certificate provider of the trust material, or {@code null}
     */
    void update(Map<String, String> sniKeyNames, @Nullable String trustName) {
        if (sniKeyNames.isEmpty()) {
            clear();
            return;
        }
        Map<String, SslContextAutoLoader> previous = byProvider;
        Map<String, SslContextAutoLoader> loaders = new HashMap<>();
        List<SslContextAutoLoader> created = new ArrayList<>();
        Map<String, SslContextAutoLoader> next = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, String> entry : sniKeyNames.entrySet()) {
                String keyName = entry.getValue();
                SslContextAutoLoader loader = loaders.get(keyName);
                if (loader == null) {
                    loader = previous.get(keyName);
                    if (loader == null) {
                        loader = loaderFactory.get();
                        // registered before loading, so that a failure below also clears it
                        created.add(loader);
                    }
                    loaders.put(keyName, loader);
                    loader.autoLoad(keyName, trustName);
                }
                next.put(entry.getKey(), loader);
            }
        } catch (RuntimeException e) {
            created.forEach(SslContextAutoLoader::clear);
            throw e;
        }
        byHostName.set(Map.copyOf(next));
        byProvider = loaders;
        previous.forEach((keyName, loader) -> {
            if (!loaders.containsKey(keyName)) {
                loader.clear();
            }
        });
    }

    /**
     * Clear all loaders.
     */
    void clear() {
        Map<String, SslContextAutoLoader> old = byProvider;
        byHostName.set(Map.of());
        byProvider = Map.of();
        old.values().forEach(SslContextAutoLoader::clear);
    }

    /**
     * The HTTP/3 context for the server name of a client hello. Netty takes its own reference to
     * the returned context only after the server name callback returns, so ours is released later
     * on the listener's event loop, where that callback runs: an update that replaces the context
     * in between cannot release it before Netty has retained it.
     *
     * @param serverName The server name
     * @param fallback   The default context, for names without a host specific certificate: the
     *                   QUIC mapping must not return {@code null}
     * @param channel    The listener's channel, or {@code null}
     * @return The context, or {@code null} if no context is available
     */
    @Nullable QuicSslContext quicContext(String serverName, SslContextAutoLoader fallback, @Nullable Channel channel) {
        SslContextAutoLoader loader = selector().find(serverName);
        SslContextHolder holder = loader == null ? null : loader.takeRetained();
        if (holder == null) {
            holder = fallback.takeRetained();
        }
        if (holder == null) {
            return null;
        }
        QuicSslContext context = holder.quicSslContext();
        releaseLater(holder, channel);
        return context;
    }

    @SuppressWarnings("resource") // the event loop belongs to the listener's channel
    private static void releaseLater(SslContextHolder holder, @Nullable Channel channel) {
        if (channel == null) {
            holder.release();
            return;
        }
        try {
            channel.eventLoop().execute(holder::release);
        } catch (RejectedExecutionException e) {
            // the listener is shutting down
            holder.release();
        }
    }
}
