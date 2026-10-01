package io.micronaut.context.python.netty;

import io.micronaut.http.netty.channel.NettyChannelType;
import io.netty.channel.EventLoop;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Channel classes must follow the transport of the event loop: the Python event loop must run on whatever transport the Netty event loop uses: NIO everywhere,
 * kqueue on macOS, epoll (and io_uring) on Linux. Netty 4.2 event loops are all
 * {@code SingleThreadIoEventLoop}s, so the transport has to be asked for, not read off the class name.
 */
final class NettyPythonEventLoopSupportTest {

    record TransportCase(NettyPythonEventLoopSupport.Transport transport, IoHandlerFactory ioHandlerFactory) {
    }

    static List<TransportCase> availableTransports() {
        List<TransportCase> cases = new ArrayList<>();
        cases.add(new TransportCase(NettyPythonEventLoopSupport.Transport.NIO, NioIoHandler.newFactory()));
        if (KQueue.isAvailable()) {
            cases.add(new TransportCase(NettyPythonEventLoopSupport.Transport.KQUEUE, KQueueIoHandler.newFactory()));
        }
        if (Epoll.isAvailable()) {
            cases.add(new TransportCase(NettyPythonEventLoopSupport.Transport.EPOLL, EpollIoHandler.newFactory()));
        }
        return cases;
    }

    @TestFactory
    List<DynamicTest> channelClassesFollowTheEventLoopTransport() {
        List<DynamicTest> tests = new ArrayList<>();
        for (TransportCase transportCase : availableTransports()) {
            tests.add(DynamicTest.dynamicTest(transportCase.transport().name(), () -> {
                try (MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transportCase.ioHandlerFactory())) {
                    EventLoop eventLoop = group.next();
                    NettyPythonEventLoopSupport support = new NettyPythonEventLoopSupport();
                    assertEquals(transportCase.transport(), support.transport(eventLoop));
                    String expectedPrefix = switch (transportCase.transport()) {
                        case NIO -> "io.netty.channel.socket.nio.Nio";
                        case EPOLL -> "io.netty.channel.epoll.Epoll";
                        case KQUEUE -> "io.netty.channel.kqueue.KQueue";
                        case IO_URING -> "io.netty.channel.uring.IoUring";
                    };
                    for (NettyChannelType type : NettyChannelType.values()) {
                        String channelClass = support.channelClass(eventLoop, type).getName();
                        assertTrue(channelClass.startsWith(expectedPrefix), type + " -> " + channelClass);
                        // the class must register with this loop, which is what the old class-name check got wrong
                        assertTrue(eventLoop.register(support.newChannel(eventLoop, type)).syncUninterruptibly().isSuccess(), channelClass);
                    }
                    assertEquals(transportCase.transport().isNative(), support.reusePortOption(eventLoop) != null);
                }
            }));
        }
        return tests;
    }

    @Test
    void tlsTimeoutsFollowAsyncioDefaultsAndNeverRoundToZero() {
        assertEquals(60_000, NettyPythonEventLoopSupport.timeoutMillis(null, NettyPythonEventLoopSupport.DEFAULT_SSL_HANDSHAKE_TIMEOUT));
        assertEquals(30_000, NettyPythonEventLoopSupport.timeoutMillis(null, NettyPythonEventLoopSupport.DEFAULT_SSL_SHUTDOWN_TIMEOUT));
        assertEquals(1, NettyPythonEventLoopSupport.timeoutMillis(0.0001, 60.0), "a sub-millisecond timeout must not become Netty's no-timeout zero");
        assertEquals(1500, NettyPythonEventLoopSupport.timeoutMillis(1.5, 60.0));
    }

    @Test
    void anSslHandlerGetsAsyncioTimeoutsWhenNoneAreGiven() throws Exception {
        NettyPythonEventLoopSupport support = new NettyPythonEventLoopSupport();
        NettyPythonEventLoopSupport.TlsOptions options = support.tlsOptions(Boolean.TRUE, false, "localhost", 443, null, null);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            SslHandler handler = support.sslHandler(channel, options);
            assertEquals(60_000, handler.getHandshakeTimeoutMillis());
            assertEquals(30_000, handler.getCloseNotifyFlushTimeoutMillis());
            assertEquals(30_000, handler.getCloseNotifyReadTimeoutMillis());
            SslHandler explicit = support.sslHandler(channel, support.tlsOptions(Boolean.TRUE, false, "localhost", 443, 0.0002, 2.5));
            assertEquals(1, explicit.getHandshakeTimeoutMillis());
            assertEquals(2500, explicit.getCloseNotifyFlushTimeoutMillis());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void shutdownWaitsForOperationsInFlightAndForTheirLateChannels() throws Exception {
        NettyPythonEventLoopSupport support = new NettyPythonEventLoopSupport();
        // a connect or bind has begun; its channel does not exist yet
        Runnable operationDone = support.beginOperation();
        CompletableFuture<Void> shutdown = support.closeAll().toCompletableFuture();
        assertFalse(shutdown.isDone(), "shutdown completed while an operation was in flight");

        // the operation's channel appears after shutdown began: closed at once, and awaited
        EmbeddedChannel late = new EmbeddedChannel();
        support.track(late);
        late.runPendingTasks();
        assertFalse(late.isOpen(), "a channel tracked after shutdown stayed open");
        assertFalse(shutdown.isDone(), "shutdown completed before the operation finished");
        operationDone.run();
        shutdown.get(5, TimeUnit.SECONDS);

        assertThrows(IllegalStateException.class, support::beginOperation, "an operation began after shutdown");
        try (MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())) {
            EventLoop lateLoop = group.next();
            assertThrows(IllegalStateException.class, () -> support.resolver(lateLoop), "a resolver was created after shutdown");
        }
    }

}
