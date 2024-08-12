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
    private KeyStore keyStoreCache = null;
    private KeyStore trustStoreCache = null;

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
        if (trustStoreCache == null) {
            super.getTrustStore(ssl).ifPresent(trustStore -> trustStoreCache = trustStore);
        }
        return Optional.ofNullable(trustStoreCache);
    }

    @Override
    protected Optional<KeyStore> getKeyStore(SslConfiguration ssl) throws Exception {
        if (keyStoreCache == null) {
            super.getKeyStore(ssl).ifPresent(keyStore -> keyStoreCache = keyStore);
        }
        return Optional.ofNullable(keyStoreCache);
    }

    @Override
    public Set<String> getObservedConfigurationPrefixes() {
        return CollectionUtils.setOf(
                SslConfiguration.PREFIX,
                ServerSslConfiguration.PREFIX
        );
    }

    @Override
    public void onApplicationEvent(RefreshEvent event) {
        // clear caches
        keyStoreCache = null;
        trustStoreCache = null;
    }

    @Override
    public int getOrder() {
        return RefreshEventListener.DEFAULT_POSITION - 10;
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
