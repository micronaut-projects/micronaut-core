package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;

@Internal
interface FlowControl {
    void read();
}
