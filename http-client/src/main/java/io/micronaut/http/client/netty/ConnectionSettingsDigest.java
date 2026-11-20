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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.ssl.AbstractClientSslConfiguration;
import io.micronaut.http.ssl.SslConfiguration;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * A digest of the settings of a client under which its connections were established: its TLS context and the
 * verification of the remote, and its proxy. Two clients may take back each other's idle connections only when their
 * digests are equal, since a connection taken back does not repeat its handshake. Secrets such as passwords only
 * enter the digest, never the result in clear.
 *
 * @since 5.3.0
 */
@Internal
final class ConnectionSettingsDigest {

    private final MessageDigest digest;

    private ConnectionSettingsDigest() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The digest of the connection settings of a client configuration.
     *
     * @param configuration The client configuration
     * @return The digest, or {@code null} if a setting is an object whose behaviour cannot be compared, such as a
     * ready {@link javax.net.ssl.SSLContext}, a {@link javax.net.ssl.HostnameVerifier}, a {@link java.net.ProxySelector}
     * or a certificate provider
     */
    static @Nullable String of(HttpClientConfiguration configuration) {
        SslConfiguration ssl = configuration.getSslConfiguration();
        if (ssl.getSslContext() != null || ssl.getKeyName() != null || ssl.getTrustName() != null
            || ssl instanceof AbstractClientSslConfiguration client && client.getHostnameVerifier() != null
            || configuration.getProxySelector().isPresent()) {
            return null;
        }
        ConnectionSettingsDigest d = new ConnectionSettingsDigest();
        d.add(ssl.getClass().getName());
        d.add(ssl.isEnabled());
        if (ssl instanceof AbstractClientSslConfiguration client) {
            d.add(client.isInsecureTrustAllCertificates());
            d.add(client.isEndpointIdentification());
        } else {
            d.add(null);
            d.add(null);
        }
        d.add(ssl.getProtocol().orElse(null));
        d.add(ssl.getProtocols().map(Arrays::toString).orElse(null));
        d.add(ssl.getCiphers().map(Arrays::toString).orElse(null));
        d.add(ssl.getClientAuthentication().map(Enum::name).orElse(null));
        d.add(ssl.isPreferOpenssl());

        SslConfiguration.KeyConfiguration key = ssl.getKey();
        d.add(key == null ? null : key.getAlias().orElse(null));
        d.add(key == null ? null : key.getPassword().orElse(null));
        SslConfiguration.KeyStoreConfiguration keyStore = ssl.getKeyStore();
        d.add(keyStore == null ? null : keyStore.getPath().orElse(null));
        d.add(keyStore == null ? null : keyStore.getKeyPath());
        d.add(keyStore == null ? null : keyStore.getCertificatePath());
        d.add(keyStore == null ? null : keyStore.getType().orElse(null));
        d.add(keyStore == null ? null : keyStore.getProvider().orElse(null));
        d.add(keyStore == null ? null : keyStore.getPassword().orElse(null));
        SslConfiguration.TrustStoreConfiguration trustStore = ssl.getTrustStore();
        d.add(trustStore == null ? null : trustStore.getPath().orElse(null));
        d.add(trustStore == null ? null : trustStore.getType().orElse(null));
        d.add(trustStore == null ? null : trustStore.getProvider().orElse(null));
        d.add(trustStore == null ? null : trustStore.getPassword().orElse(null));

        d.add(configuration.getProxyType().name());
        d.add(configuration.getProxyAddress().map(Object::toString).orElse(null));
        d.add(configuration.getProxyUsername().orElse(null));
        d.add(configuration.getProxyPassword().orElse(null));
        return HexFormat.of().formatHex(d.digest.digest());
    }

    private void add(boolean value) {
        add(String.valueOf(value));
    }

    /**
     * Adds a value, prefixed by its length so that no two sequences of values give the same input.
     */
    private void add(@Nullable String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(new byte[] {(byte) (bytes.length >>> 24), (byte) (bytes.length >>> 16), (byte) (bytes.length >>> 8), (byte) bytes.length});
        digest.update(bytes);
    }
}
