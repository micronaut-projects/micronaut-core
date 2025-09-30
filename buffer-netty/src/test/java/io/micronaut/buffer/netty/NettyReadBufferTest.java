package io.micronaut.buffer.netty;

import io.netty.buffer.ByteBufAllocator;

public class NettyReadBufferTest extends AbstractReadBufferTest {
    public NettyReadBufferTest() {
        super(NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT));
    }
}
