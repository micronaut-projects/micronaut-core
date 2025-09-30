package io.micronaut.web.router.uri;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

final class WhatwgUrl {
    final @NonNull String scheme;
    final @NonNull String username;
    final @NonNull String password;
    final @Nullable String host;
    final @Nullable Integer port;
    final @NonNull String path;
    final boolean opaquePath;
    final @Nullable String query;
    final @Nullable String fragment;

    public WhatwgUrl(@NonNull String scheme, @NonNull String username, @NonNull String password, @Nullable String host, @Nullable Integer port, @NonNull String path, boolean opaquePath, @Nullable String query, @Nullable String fragment) {
        this.scheme = scheme;
        this.username = username;
        this.password = password;
        this.host = host;
        this.port = port;
        this.path = path;
        this.opaquePath = opaquePath;
        this.query = query;
        this.fragment = fragment;
    }

    void serialize(StringBuilder builder, boolean excludeFragment) {
        builder.append(scheme).append(':');
        if (host != null) {
            builder.append("//");
            if (!username.isEmpty() || !password.isEmpty()) {
                builder.append(username);
                if (!password.isEmpty()) {
                    builder.append(":").append(password);
                }
                builder.append('@');
            }
            builder.append(host);
            if (port != null) {
                builder.append(":").append(port);
            }
        }
        builder.append(path);
        if (query != null) {
            builder.append("?").append(query);
        }
        if (fragment != null && !excludeFragment) {
            builder.append("#").append(fragment);
        }
    }
}
