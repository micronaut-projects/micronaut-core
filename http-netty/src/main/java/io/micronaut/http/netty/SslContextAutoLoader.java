/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.netty;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.ssl.CertificateProvider;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuples;

import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Automatically loads and refreshes Netty SSL contexts from configured {@link CertificateProvider}s.
 * Subclasses supply the configuration, transport (TCP vs QUIC), and builder factory. This class
 * subscribes to keystore/truststore publishers and swaps the active {@link SslContextHolder}
 * when updates arrive, taking care of Netty reference counting.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
@Internal
public abstract class SslContextAutoLoader {
    private final Logger log;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    @Nullable
    private SslContextHolder current;
    private Disposable refreshSslDisposable;
    private long generation;

    /**
     * Create a new auto-loader.
     *
     * @param log logger used to report initialization failures
     */
    protected SslContextAutoLoader(Logger log) {
        this.log = log;
    }

    private void replace(@Nullable SslContextHolder holder, long gen) {
        rwLock.writeLock().lock();
        try {
            if (gen < this.generation) {
                if (holder != null) {
                    holder.release();
                }
                return;
            }
            assert gen == this.generation;

            if (current != null) {
                current.release();
            }
            current = holder;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Obtain the current SSL context holder and retain the underlying Netty contexts.
     *
     * @return the retained holder, or {@code null} if no context is currently available
     */
    @Nullable
    public final SslContextHolder takeRetained() {
        rwLock.readLock().lock();
        try {
            if (current != null) {
                current.retain();
            }
            return current;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Stop watching for updates and release the current SSL context holder.
     * Safe to call multiple times.
     */
    public final void clear() {
        Disposable d;
        rwLock.writeLock().lock();
        try {
            d = refreshSslDisposable;
            refreshSslDisposable = null;
            if (current != null) {
                current.release();
                current = null;
            }
            generation++;
        } finally {
            rwLock.writeLock().unlock();
        }
        if (d != null) {
            d.dispose();
        }
    }

    /**
     * Access to named {@link CertificateProvider} beans used to resolve key/trust material.
     *
     * @return a provider of {@link CertificateProvider} beans
     */
    protected abstract @NonNull BeanProvider<CertificateProvider> certificateProviders();

    /**
     * The SSL configuration used to derive defaults like protocols, ciphers and client auth.
     *
     * @return the SSL configuration
     */
    protected abstract @NonNull SslConfiguration sslConfiguration();

    /**
     * Whether the target transport is QUIC/HTTP3 (true) or TCP (false).
     *
     * @return {@code true} for QUIC, {@code false} for TCP
     */
    protected abstract boolean quic();

    /**
     * Create the legacy SSL context holder when no certificate providers are configured.
     * Implementations should read from legacy configuration and build fixed contexts.
     *
     * @return a holder for legacy contexts
     */
    protected abstract @NonNull SslContextHolder createLegacy();

    /**
     * Start auto-loading using names from {@link SslConfiguration}
     * ({@link SslConfiguration#getKeyName()} and {@link SslConfiguration#getTrustName()}).
     */
    public final void autoLoad() {
        autoLoad(sslConfiguration().getKeyName(), sslConfiguration().getTrustName());
    }

    /**
     * Start auto-loading using the given provider names.
     *
     * @param keyName   optional name of the {@link CertificateProvider} for the key store
     * @param trustName optional name of the {@link CertificateProvider} for the trust store
     */
    public final void autoLoad(@Nullable String keyName, @Nullable String trustName) {
        long gen;
        Disposable d;
        rwLock.writeLock().lock();
        try {
            gen = ++generation;
            d = refreshSslDisposable;
            refreshSslDisposable = null;
        } finally {
            rwLock.writeLock().unlock();
        }
        if (d != null) {
            d.dispose();
        }
        Disposable nextDisposable;
        if (keyName == null && trustName == null) {
            // legacy code path
            replace(createLegacy(), gen);
            nextDisposable = null;
        } else if (keyName != null && trustName != null) {
            CertificateProvider keyProvider = certificateProviders().get(Qualifiers.byName(keyName));
            CertificateProvider trustProvider = certificateProviders().get(Qualifiers.byName(trustName));
            nextDisposable = Flux.combineLatest(keyProvider.getKeyStore(), trustProvider.getTrustStore(), Tuples::of)
                .subscribe(tuple -> refreshSsl(tuple.getT1(), tuple.getT2(), gen));
        } else if (keyName != null) {
            CertificateProvider keyProvider = certificateProviders().get(Qualifiers.byName(keyName));
            nextDisposable = Flux.from(keyProvider.getKeyStore())
                .subscribe(ks -> refreshSsl(ks, null, gen));
        } else {
            CertificateProvider trustProvider = certificateProviders().get(Qualifiers.byName(trustName));
            nextDisposable = Flux.from(trustProvider.getTrustStore())
                .subscribe(ts -> refreshSsl(null, ts, gen));
        }
        if (nextDisposable != null) {
            rwLock.writeLock().lock();
            try {
                if (generation == gen) {
                    refreshSslDisposable = nextDisposable;
                } else {
                    nextDisposable.dispose();
                }
            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }

    /**
     * Create a new {@link NettySslContextBuilder} in server or client mode depending on the subclass.
     *
     * @return the builder to construct Netty SSL contexts
     */
    protected abstract @NonNull NettySslContextBuilder builder();

    /**
     * Build fresh SSL contexts from the supplied key/trust stores and swap the active holder.
     *
     * @param ks  the key store, or {@code null}
     * @param ts  the trust store, or {@code null}
     * @param gen generation stamp ensuring only the latest update wins
     */
    private void refreshSsl(@Nullable KeyStore ks, @Nullable KeyStore ts, long gen) {
        try {
            NettySslContextBuilder builder = builder()
                .openssl(NettyTlsUtils.useOpenssl(sslConfiguration()))
                .keyStore(ks)
                .keyPassword(sslConfiguration().getKey().getPassword()
                    .or(() -> sslConfiguration().getKeyStore().getPassword())
                    .orElse(null))
                .trustStore(ts)
                .clientAuthentication(sslConfiguration().getClientAuthentication().orElse(null))
                .ciphers(sslConfiguration().getCiphers().map(List::of).orElse(null), false)
                .protocols(sslConfiguration().getProtocols().map(List::of).orElse(null));

            replace(quic() ?
                    new SslContextHolder(null, builder.buildHttp3()) :
                    new SslContextHolder(builder.buildTcp(), null),
                gen);
        } catch (Exception e) {
            log.warn("Failed to initialize SSL context", e);
        }
    }
}
