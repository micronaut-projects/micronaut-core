package io.micronaut.http.server.netty.nativetransport;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.netty.channel.DefaultEventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupFactory;
import io.micronaut.http.netty.channel.NettyChannelType;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TransportConfigTest {
    @Test
    public void unavailable() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "micronaut.netty.event-loops.default.transport", "unavailable"
        ))) {
            EventLoopGroupFactory factory = ctx.getBean(EventLoopGroupFactory.class);
            assertThrows(NoSuchElementException.class, () -> factory.channelClass(NettyChannelType.SERVER_SOCKET, ctx.getBean(DefaultEventLoopGroupConfiguration.class)));
        }
    }

    @Test
    public void single() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "micronaut.netty.event-loops.default.transport", "nio"
        ))) {
            EventLoopGroupFactory factory = ctx.getBean(EventLoopGroupFactory.class);
            assertEquals(NioServerSocketChannel.class, factory.channelClass(NettyChannelType.SERVER_SOCKET, ctx.getBean(DefaultEventLoopGroupConfiguration.class)));
        }
    }

    @Test
    public void multi() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "micronaut.netty.event-loops.default.transport", "nio,foo1,foo2"
        ))) {
            EventLoopGroupFactory factory = ctx.getBean(EventLoopGroupFactory.class);
            assertEquals(NioServerSocketChannel.class, factory.channelClass(NettyChannelType.SERVER_SOCKET, ctx.getBean(DefaultEventLoopGroupConfiguration.class)));
        }
    }
}
