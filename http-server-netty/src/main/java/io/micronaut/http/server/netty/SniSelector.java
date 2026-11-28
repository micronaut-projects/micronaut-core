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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.SslContextAutoLoader;
import io.micronaut.http.netty.SslContextHolder;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Selects the SSL context of a listener by the TLS server name (SNI). Exact host names take
 * precedence over wildcard entries ({@code *.example.com}, matching exactly one label).
 *
 * @param loaders The current SSL context loaders by normalized host name or wildcard pattern.
 *                A supplier, so that a handshake still pending when the configuration is
 *                refreshed selects from the loaders that are current when it completes.
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record SniSelector(Supplier<Map<String, SslContextAutoLoader>> loaders) {

    /**
     * Find the loader for the given server name.
     *
     * @param serverName The server name from the client hello, or {@code null}
     * @return The loader, or {@code null} if the default certificate applies
     */
    @Nullable
    SslContextAutoLoader find(@Nullable String serverName) {
        if (serverName == null) {
            return null;
        }
        Map<String, SslContextAutoLoader> byHostName = loaders.get();
        if (byHostName.isEmpty()) {
            return null;
        }
        String name = serverName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        SslContextAutoLoader exact = byHostName.get(name);
        if (exact != null) {
            return exact;
        }
        int dot = name.indexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return byHostName.get("*" + name.substring(dot));
        }
        return null;
    }

    /**
     * Take the current context for the given server name.
     *
     * @param serverName The server name from the client hello, or {@code null}
     * @return The retained holder, or {@code null} if the default certificate applies or the
     * host specific context is not available
     */
    @Nullable
    SslContextHolder takeRetained(@Nullable String serverName) {
        SslContextAutoLoader loader = find(serverName);
        return loader == null ? null : loader.takeRetained();
    }
}
