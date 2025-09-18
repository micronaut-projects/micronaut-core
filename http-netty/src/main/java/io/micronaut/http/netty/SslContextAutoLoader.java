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

@Internal
public abstract class SslContextAutoLoader {
    private final Logger log;

    @Nullable
    private SslContextHolder current;
    private Disposable refreshSslDisposable;
    private long generation;

    protected SslContextAutoLoader(Logger log) {
        this.log = log;
    }

    private synchronized void replace(@Nullable SslContextHolder holder, long gen) {
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
    }

    @Nullable
    public final synchronized SslContextHolder takeRetained() {
        if (current != null) {
            current.retain();
        }
        return current;
    }

    public final void clear() {
        Disposable d;
        synchronized (this) {
            d = refreshSslDisposable;
            refreshSslDisposable = null;
            if (current != null) {
                current.release();
                current = null;
            }
            generation++;
        }
        if (d != null) {
            d.dispose();
        }
    }

    protected abstract BeanProvider<CertificateProvider> certificateProviders();

    protected abstract SslConfiguration sslConfiguration();

    protected abstract boolean quic();

    protected abstract SslContextHolder createLegacy();

    public final void autoLoad() {
        autoLoad(sslConfiguration().getKeyName(), sslConfiguration().getTrustName());
    }

    public final void autoLoad(@Nullable String keyName, @Nullable String trustName) {
        long gen;
        Disposable d;
        synchronized (this) {
            gen = ++generation;
            d = refreshSslDisposable;
            refreshSslDisposable = null;
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
            synchronized (this) {
                if (generation == gen) {
                    refreshSslDisposable = nextDisposable;
                } else {
                    nextDisposable.dispose();
                }
            }
        }
    }

    protected abstract NettySslContextBuilder builder();

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
