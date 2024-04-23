package io.micronaut.http.body;

import java.io.Closeable;

public interface CloseableInboundByteBody extends InboundByteBody, Closeable {
    @Override
    void close();
}
