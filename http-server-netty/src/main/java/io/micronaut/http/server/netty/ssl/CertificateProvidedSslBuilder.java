/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.http.ssl.SslBuilder;
import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import io.micronaut.runtime.context.scope.refresh.RefreshEventListener;
import io.netty.handler.ssl.SslContext;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.security.KeyStore;
import java.util.Optional;
import java.util.Set;

/**
 * The Netty implementation of {@link SslBuilder} that generates an {@link SslContext} to create a server handle with
 * SSL support via user configuration.
 */
@Requires(condition = SslEnabledCondition.class)
@Requires(condition = CertificateProvidedSslBuilder.SelfSignedNotConfigured.class)
@Singleton
@Internal
public class CertificateProvidedSslBuilder extends AbstractServerSslBuilder implements ServerSslBuilder, RefreshEventListener, Ordered {

    private final ServerSslConfiguration ssl;
    @Nullable
    private volatile CachedStore keyStoreCache = null;
    @Nullable
    private volatile CachedStore trustStoreCache = null;

    /**
     * @param httpServerConfiguration The HTTP server configuration
     * @param ssl                     The ssl configuration
     * @param resourceResolver        The resource resolver
     */
    public CertificateProvidedSslBuilder(
            HttpServerConfiguration httpServerConfiguration,
            ServerSslConfiguration ssl,
            ResourceResolver resourceResolver) {
        super(resourceResolver, httpServerConfiguration);
        this.ssl = ssl;
    }

    @Override
    public ServerSslConfiguration getSslConfiguration() {
        return ssl;
    }

    @Override
    protected Optional<KeyStore> getTrustStore(SslConfiguration ssl) throws Exception {
        SslConfiguration.TrustStoreConfiguration trustStore = ssl.getTrustStore();
        StoreSettings settings = new StoreSettings(trustStore.getPath().orElse(null), null, null,
            trustStore.getPassword().orElse(null), trustStore.getType().orElse(null), trustStore.getProvider().orElse(null));
        CachedStore cached = trustStoreCache;
        if (cached == null || !cached.settings().equals(settings)) {
            cached = new CachedStore(settings, super.getTrustStore(ssl).orElse(null));
            trustStoreCache = cached;
        }
        return Optional.ofNullable(cached.store());
    }

    @Override
    protected Optional<KeyStore> getKeyStore(SslConfiguration ssl) throws Exception {
        SslConfiguration.KeyStoreConfiguration keyStore = ssl.getKeyStore();
        StoreSettings settings = new StoreSettings(keyStore.getPath().orElse(null), keyStore.getKeyPath(), keyStore.getCertificatePath(),
            keyStore.getPassword().orElse(null), keyStore.getType().orElse(null), keyStore.getProvider().orElse(null));
        CachedStore cached = keyStoreCache;
        if (cached == null || !cached.settings().equals(settings)) {
            cached = new CachedStore(settings, super.getKeyStore(ssl).orElse(null));
            keyStoreCache = cached;
        }
        return Optional.ofNullable(cached.store());
    }

    @Override
    public Set<String> getObservedConfigurationPrefixes() {
        return CollectionUtils.setOf(
                SslConfiguration.PREFIX,
                ServerSslConfiguration.PREFIX
        );
    }

    @Override
    public void reload() {
        // a store is cached with the settings it was loaded from, so a changed setting is a miss on its own; clearing
        // reloads an unchanged path too, for a store file rewritten in place
        keyStoreCache = null;
        trustStoreCache = null;
    }

    @Override
    public void onApplicationEvent(RefreshEvent event) {
        reload();
    }

    @Override
    public int getOrder() {
        return RefreshEventListener.DEFAULT_POSITION - 10;
    }

    /**
     * The settings a store was loaded from: a store loaded for other settings is stale, whichever
     * notification of the change arrives first.
     *
     * @param path The store path
     * @param keyPath The PEM key path
     * @param certificatePath The PEM certificate path
     * @param password The password
     * @param type The store type
     * @param provider The provider
     */
    private record StoreSettings(@Nullable String path, @Nullable String keyPath, @Nullable String certificatePath,
                                 @Nullable String password, @Nullable String type, @Nullable String provider) {
    }

    private record CachedStore(StoreSettings settings, @Nullable KeyStore store) {
    }

    static class SelfSignedNotConfigured extends BuildSelfSignedCondition {
        @Override
        protected boolean validate(ConditionContext context, boolean deprecatedPropertyFound, boolean newPropertyFound) {
            if (deprecatedPropertyFound) {
                context.fail("Deprecated  " + SslConfiguration.PREFIX + ".build-self-signed config detected, disabling provided certificate.");
                return false;
            } else if (newPropertyFound) {
                context.fail(ServerSslConfiguration.PREFIX + ".build-self-signed config detected, disabling provided certificate.");
                return false;
            } else {
                return true;
            }
        }
    }
}
