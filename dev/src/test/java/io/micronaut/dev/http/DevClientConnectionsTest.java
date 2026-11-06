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
package io.micronaut.dev.http;

import io.micronaut.http.netty.channel.DefaultEventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.NioEventLoopGroupFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The connections kept across generations, on an event loop of a retained group.
 */
class DevClientConnectionsTest {

    private final DevEventLoopGroups groups = new DevEventLoopGroups();
    private final DevClientConnections connections = new DevClientConnections(groups);
    private EventLoopGroup group;
    private Channel server;
    private LocalAddress address;

    @BeforeEach
    void setUp() throws InterruptedException {
        DefaultEventLoopGroupConfiguration configuration = new DefaultEventLoopGroupConfiguration("default", 1, 1.0, null, false, null, Duration.ZERO, Duration.ofSeconds(1), false);
        group = groups.eventLoopGroup("default", configuration, new NioEventLoopGroupFactory(), () -> new MultiThreadIoEventLoopGroup(1, LocalIoHandler.newFactory()));
        address = new LocalAddress("dev-client-connections-" + UUID.randomUUID());
        server = new ServerBootstrap().group(group).channel(LocalServerChannel.class).childHandler(new ChannelInboundHandlerAdapter()).bind(address).sync().channel();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        connections.close();
        server.close().sync();
        groups.close();
    }

    @Test
    void aConnectionIsTakenBackByAClientOfItsConfigurationToItsRemoteOnItsEventLoop() throws InterruptedException {
        Channel channel = connect();
        EventLoop loop = channel.eventLoop();

        assertTrue(connections.retain("client", "remote", channel));
        assertEquals(1, connections.size());
        assertEquals(List.of(new AbstractMap.SimpleImmutableEntry<>("remote", loop)), connections.retained("client"));
        assertEquals(List.of(), connections.retained("other"));
        assertNull(connections.adopt("client", "elsewhere", loop));
        assertSame(channel, connections.adopt("client", "remote", loop));
        assertEquals(0, connections.size());
        assertTrue(channel.isActive());
    }

    @Test
    void aConnectionNoClientTookBackDuringTheNextGenerationIsClosedAsItEnds() throws InterruptedException {
        Channel channel = connect();
        assertTrue(connections.retain("client", "remote", channel));

        // kept as a generation stops, for the next one
        connections.generationEnded();
        assertEquals(1, connections.size());
        // which did not take it back
        connections.generationEnded();
        assertEquals(0, connections.size());
        channel.closeFuture().sync();
    }

    @Test
    void aConnectionTheRemoteClosesIsNoLongerKept() throws InterruptedException {
        Channel channel = connect();
        assertTrue(connections.retain("client", "remote", channel));

        channel.close().sync();
        // the close listener runs on the channel's event loop, ahead of this task
        channel.eventLoop().submit(() -> { }).sync();
        assertEquals(0, connections.size());
    }

    @Test
    void onlyAnActiveConnectionOnARetainedEventLoopIsKeptAndNoneOnceClosed() throws InterruptedException {
        EmbeddedChannel elsewhere = new EmbeddedChannel();
        assertFalse(connections.retain("client", "remote", elsewhere));
        elsewhere.close();
        assertFalse(connections.retain("client", "remote", elsewhere));
        assertTrue(connections.isRetainable(Arrays.asList("configuration", null)));

        Channel channel = connect();
        connections.close();
        assertFalse(connections.retain("client", "remote", channel));
        assertEquals(0, connections.size());
    }

    private Channel connect() throws InterruptedException {
        return new Bootstrap().group(group).channel(LocalChannel.class).handler(new ChannelInboundHandlerAdapter()).connect(address).sync().channel();
    }
}
