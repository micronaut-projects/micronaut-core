package io.micronaut.buffer.netty;

import io.micronaut.core.io.buffer.ReadBufferFactory;

public class NioReadBufferTest extends AbstractReadBufferTest {
    NioReadBufferTest() {
        super(ReadBufferFactory.getJdkFactory());
    }
}
