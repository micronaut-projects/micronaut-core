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
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.http.ssl.SslBuilder;
import io.micronaut.http.ssl.SslConfiguration;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import jakarta.inject.Singleton;

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Optional;

/**
 * The Netty implementation of {@link SslBuilder} that generates an {@link SslContext} to create a server handler
 * with SSL support via a generated self signed certificate.
 */
@Requires(condition = SslEnabledCondition.class)
@Requires(condition = SelfSignedSslBuilder.SelfSignedConfigured.class)
@Singleton
@Internal
public class SelfSignedSslBuilder extends AbstractServerSslBuilder implements ServerSslBuilder {
    private final ServerSslConfiguration ssl;

    /**
     * @param serverConfiguration The server configuration
     * @param ssl                 The SSL configuration
     * @param resourceResolver    The resource resolver
     */
    public SelfSignedSslBuilder(
            HttpServerConfiguration serverConfiguration,
            ServerSslConfiguration ssl,
            ResourceResolver resourceResolver) {
        super(resourceResolver, serverConfiguration);
        this.ssl = ssl;
    }

    @Override
    public ServerSslConfiguration getSslConfiguration() {
        return ssl;
    }

    @Override
    protected Optional<KeyStore> getKeyStore(SslConfiguration ssl) throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        store.setKeyEntry("key", ssc.key(), null, new Certificate[]{ssc.cert()});
        return Optional.of(store);
    }

    static class SelfSignedConfigured extends BuildSelfSignedCondition {
        @Override
        protected boolean validate(ConditionContext context, boolean deprecatedPropertyFound, boolean newPropertyFound) {
            if (!deprecatedPropertyFound && !newPropertyFound) {
                context.fail("Neither the old deprecated " + SslConfiguration.PREFIX + ".build-self-signed, nor the new " + ServerSslConfiguration.PREFIX + ".build-self-signed were enabled.");
                return false;
            } else {
                return true;
            }
        }
    }
}
