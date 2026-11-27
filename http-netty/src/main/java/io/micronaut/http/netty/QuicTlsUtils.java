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
package io.micronaut.http.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.ssl.KeyExchangeGroups;
import io.micronaut.http.ssl.SslConfiguration;
import io.netty.handler.codec.quic.BoringSSLContextOption;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.util.ReferenceCountUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TLS utilities for HTTP/3. Kept apart from {@link NettyTlsUtils} because the QUIC classes are an
 * optional dependency.
 *
 * @since 5.3.0
 */
@Internal
public final class QuicTlsUtils {
    private static final Map<String, Boolean> QUIC_GROUP_SUPPORT = new ConcurrentHashMap<>();

    private QuicTlsUtils() {
    }

    /**
     * Resolve the configured TLS key exchange groups for HTTP/3 and check that the QUIC TLS
     * implementation supports them.
     *
     * @param ssl The SSL configuration
     * @return The groups to apply, or {@code null} to keep the defaults
     * @throws io.micronaut.http.ssl.SslConfigurationException If the groups are invalid or unsupported
     * @since 5.3.0
     */
    public static @Nullable List<String> keyExchangeGroups(SslConfiguration ssl) {
        if (!KeyExchangeGroups.isConfigured(ssl)) {
            return null;
        }
        return KeyExchangeGroups.resolve(ssl, QuicTlsUtils::quicGroupSupported, "QUIC");
    }

    /**
     * Configure the key exchange groups on a QUIC context builder.
     *
     * @param sslBuilder The builder
     * @param groups The groups from {@link #keyExchangeGroups(SslConfiguration)}
     * @since 5.3.0
     */
    public static void applyKeyExchangeGroups(QuicSslContextBuilder sslBuilder, @Nullable List<String> groups) {
        if (groups != null) {
            sslBuilder.option(BoringSSLContextOption.GROUPS, groups.toArray(String[]::new));
        }
    }

    private static boolean quicGroupSupported(String group) {
        return QUIC_GROUP_SUPPORT.computeIfAbsent(group, QuicTlsUtils::probeQuicGroup);
    }

    private static boolean probeQuicGroup(String group) {
        QuicSslContext context = null;
        try {
            context = QuicSslContextBuilder.forClient()
                .option(BoringSSLContextOption.GROUPS, new String[]{group})
                .build();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            ReferenceCountUtil.release(context);
        }
    }
}
