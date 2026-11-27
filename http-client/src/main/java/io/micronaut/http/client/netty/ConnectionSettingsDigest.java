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

import javax.crypto.Mac;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * The identity of the TLS settings under which a client establishes its connections: two clients may take back each
 * other's idle connections only when these are equal, since a connection taken back does not repeat its handshake.
 * It is an HMAC keyed by the {@link RetainedClientConnections} that keeps the connections, so it can only be compared
 * with the identities computed for the same instance.
 *
 * <p>Only settings given as values take part. A client whose connections depend on more than these keeps none: one
 * with a ready {@link javax.net.ssl.SSLContext}, a {@link javax.net.ssl.HostnameVerifier}, certificate providers, a key
 * or trust store, whose files may change without a change of the configuration, or a proxy.</p>
 *
 * @since 5.3.0
 */
@Internal
final class ConnectionSettingsDigest {

    private final Mac mac;

    private ConnectionSettingsDigest(Mac mac) {
        this.mac = mac;
    }

    /**
     * The identity of the connection settings of a client configuration.
     *
     * @param configuration The client configuration
     * @param mac The keyed MAC of the {@link RetainedClientConnections} the connections are offered to
     * @return The identity, or {@code null} if the connections depend on more than the settings' values
     */
    static @Nullable String of(HttpClientConfiguration configuration, Mac mac) {
        SslConfiguration ssl = configuration.getSslConfiguration();
        if (ssl.getSslContext() != null || ssl.getKeyName() != null || ssl.getTrustName() != null
            || ssl instanceof AbstractClientSslConfiguration client && client.getHostnameVerifier() != null
            || ssl.isEnabled() && hasStore(ssl)
            // a proxied connection keeps the proxy's handler, so it is never offered; nor may such a client take back
            // a direct connection
            || configuration.getProxySelector().isPresent() || configuration.getProxyAddress().isPresent()) {
            return null;
        }
        ConnectionSettingsDigest d = new ConnectionSettingsDigest(mac);
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
        return HexFormat.of().formatHex(d.mac.doFinal());
    }

    private static boolean hasStore(SslConfiguration ssl) {
        SslConfiguration.KeyStoreConfiguration keyStore = ssl.getKeyStore();
        SslConfiguration.TrustStoreConfiguration trustStore = ssl.getTrustStore();
        return keyStore.getPath().isPresent() || keyStore.getKeyPath() != null || keyStore.getCertificatePath() != null
            || trustStore.getPath().isPresent();
    }

    private void add(boolean value) {
        add(String.valueOf(value));
    }

    /**
     * Adds a value, prefixed by its length so that no two sequences of values give the same input.
     */
    private void add(@Nullable String value) {
        if (value == null) {
            mac.update((byte) 0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        mac.update((byte) 1);
        mac.update(new byte[] {(byte) (bytes.length >>> 24), (byte) (bytes.length >>> 16), (byte) (bytes.length >>> 8), (byte) bytes.length});
        mac.update(bytes);
    }
}
