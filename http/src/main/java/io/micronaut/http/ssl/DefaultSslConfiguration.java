/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.ssl;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Primary;


/**
 * The default {@link SslConfiguration} configuration used if no other configuration is specified.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(SslConfiguration.PREFIX)
@Primary
@BootstrapContextCompatible
public class DefaultSslConfiguration extends SslConfiguration {

    /**
     * Sets the key configuration.
     *
     * @param keyConfiguration The key configuration.
     */
    void setKey(DefaultKeyConfiguration keyConfiguration) {
        if (keyConfiguration != null) {
            super.setKey(keyConfiguration);
        }
    }

    /**
     * Sets the key store.
     *
     * @param keyStoreConfiguration The key store configuration
     */
    @SuppressWarnings("unused")
    void setKeyStore(DefaultKeyStoreConfiguration keyStoreConfiguration) {
        if (keyStoreConfiguration != null) {
            super.setKeyStore(keyStoreConfiguration);
        }
    }

    /**
     * Sets trust store configuration.
     *
     * @param trustStore The trust store configuration
     */
    @SuppressWarnings("unused")
    void setTrustStore(DefaultTrustStoreConfiguration trustStore) {
        super.setTrustStore(trustStore);
    }

    /**
     * The default {@link io.micronaut.http.ssl.SslConfiguration.KeyConfiguration}.
     */
    @SuppressWarnings("WeakerAccess")
    @Primary
    @ConfigurationProperties(KeyConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class DefaultKeyConfiguration extends KeyConfiguration {
    }

    /**
     * The default {@link io.micronaut.http.ssl.SslConfiguration.KeyStoreConfiguration}.
     */
    @SuppressWarnings("WeakerAccess")
    @Primary
    @ConfigurationProperties(KeyStoreConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class DefaultKeyStoreConfiguration extends KeyStoreConfiguration {

    }

    /**
     * The default {@link io.micronaut.http.ssl.SslConfiguration.TrustStoreConfiguration}.
     */
    @SuppressWarnings("WeakerAccess")
    @Primary
    @ConfigurationProperties(TrustStoreConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class DefaultTrustStoreConfiguration extends TrustStoreConfiguration {
    }
}
