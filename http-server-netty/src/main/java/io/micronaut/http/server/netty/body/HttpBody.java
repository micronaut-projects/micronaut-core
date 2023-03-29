package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Nullable;

public interface HttpBody {
    void release();

    @Nullable
    HttpBody next();
}
