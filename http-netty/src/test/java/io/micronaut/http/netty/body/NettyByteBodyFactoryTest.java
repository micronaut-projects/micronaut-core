package io.micronaut.http.netty.body;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class NettyByteBodyFactoryTest {
    @Test
    void forChannelReturnsOneInstancePerChannel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        NettyByteBodyFactory first = NettyByteBodyFactory.forChannel(channel);
        NettyByteBodyFactory second = NettyByteBodyFactory.forChannel(channel);
        assertSame(first, second);
        channel.finishAndReleaseAll();
    }

    @Test
    void forChannelIsScopedToTheChannel() {
        EmbeddedChannel a = new EmbeddedChannel();
        EmbeddedChannel b = new EmbeddedChannel();
        assertNotSame(NettyByteBodyFactory.forChannel(a), NettyByteBodyFactory.forChannel(b));
        a.finishAndReleaseAll();
        b.finishAndReleaseAll();
    }

    @Test
    void forChannelIsIndependentOfDirectlyConstructedInstances() {
        EmbeddedChannel channel = new EmbeddedChannel();
        NettyByteBodyFactory direct = new NettyByteBodyFactory(channel);
        NettyByteBodyFactory shared = NettyByteBodyFactory.forChannel(channel);
        assertNotSame(direct, shared);
        assertSame(shared, NettyByteBodyFactory.forChannel(channel));
        channel.finishAndReleaseAll();
    }
}
