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
package io.micronaut.context.python;

import io.micronaut.context.python.netty.NettyPythonEventLoopProvider;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NettyPythonAsyncioRuntimeTest {

    @Test
    void nettyBackedRuntimeRunsSleepAndCallbacksOnEventLoop() throws Exception {
        DefaultEventLoop eventLoop = new DefaultEventLoop();
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(new NettyPythonEventLoopProvider()));
        try {
            NettyPythonEventLoopProvider.bind(eventLoop, () -> {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                import java
                Thread = java.type("java.lang.Thread")
                async def run():
                    loop = asyncio.get_running_loop()
                    future = loop.create_future()
                    before = Thread.currentThread().getName()
                    loop.call_soon(future.set_result, Thread.currentThread().getName())
                    callback_thread = await future
                    await asyncio.sleep(0.001)
                    after = Thread.currentThread().getName()
                    return before + "|" + callback_thread + "|" + after
                run()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            String threadNames = stage.toCompletableFuture().get(5, TimeUnit.SECONDS).toString();
            int firstSeparator = threadNames.indexOf('|');
            int secondSeparator = threadNames.indexOf('|', firstSeparator + 1);
            String before = threadNames.substring(0, firstSeparator);
            String callback = threadNames.substring(firstSeparator + 1, secondSeparator);
            String after = threadNames.substring(secondSeparator + 1);
            assertEquals(before, callback);
            assertEquals(before, after);
            assertTrue(before.contains("defaultEventLoop"), before);
            return null;
            });
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            context.close(true);
            eventLoop.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void nettyBackedRuntimeRunsCreateDatagramEndpoint() throws Exception {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        EventLoop eventLoop = eventLoopGroup.next();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(new NettyPythonEventLoopProvider()));
        PythonAsyncioRuntime.setExecutorService(executorService);
        try {
            NettyPythonEventLoopProvider.bind(eventLoop, () -> {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                class Server(asyncio.DatagramProtocol):
                    def connection_made(self, transport):
                        self.transport = transport
                    def datagram_received(self, data, addr):
                        self.transport.sendto(b"echo:" + data, addr)
                class Client(asyncio.DatagramProtocol):
                    def __init__(self, done):
                        self.done = done
                    def connection_made(self, transport):
                        transport.sendto(b"ok")
                    def datagram_received(self, data, addr):
                        self.done.set_result(data.decode())
                async def run():
                    loop = asyncio.get_running_loop()
                    server_transport, _ = await loop.create_datagram_endpoint(Server, local_addr=("127.0.0.1", 0))
                    assert server_transport.get_extra_info("micronaut.netty") is True
                    done = loop.create_future()
                    client_transport, _ = await loop.create_datagram_endpoint(lambda: Client(done), remote_addr=server_transport.get_extra_info("sockname"))
                    assert client_transport.get_extra_info("micronaut.netty") is True
                    try:
                        return await done
                    finally:
                        client_transport.close()
                        server_transport.close()
                run()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            assertEquals("echo:ok", stage.toCompletableFuture().get(5, TimeUnit.SECONDS));
            return null;
            });
        } finally {
            PythonAsyncioRuntime.setExecutorService(null);
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            executorService.shutdownNow();
            context.close(true);
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void nettyBackedRuntimeRunsCreateConnectionAndCreateServer() throws Exception {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        EventLoop eventLoop = eventLoopGroup.next();
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(new NettyPythonEventLoopProvider()));
        try {
            NettyPythonEventLoopProvider.bind(eventLoop, () -> {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                class Echo(asyncio.Protocol):
                    def connection_made(self, transport):
                        assert transport.get_extra_info("micronaut.netty") is True
                        self.transport = transport
                    def data_received(self, data):
                        self.transport.write(b"echo:" + data)
                        self.transport.close()
                class Client(asyncio.Protocol):
                    def __init__(self, done):
                        self.done = done
                    def connection_made(self, transport):
                        assert transport.get_extra_info("micronaut.netty") is True
                        self.transport = transport
                        transport.write(b"ok")
                    def data_received(self, data):
                        self.done.set_result(data.decode())
                    def connection_lost(self, exc):
                        pass
                async def run():
                    loop = asyncio.get_running_loop()
                    server = await loop.create_server(Echo, "127.0.0.1", 0)
                    done = loop.create_future()
                    transport, _ = await loop.create_connection(lambda: Client(done), *server.sockets[0].getsockname())
                    try:
                        return await done
                    finally:
                        transport.close()
                        server.close()
                        await server.wait_closed()
                run()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            assertEquals("echo:ok", stage.toCompletableFuture().get(5, TimeUnit.SECONDS));
            return null;
            });
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            context.close(true);
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void rejectsPythonSslContextObjectsBeforeConnecting() throws Exception {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        EventLoop eventLoop = eventLoopGroup.next();
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(new NettyPythonEventLoopProvider()));
        try {
            NettyPythonEventLoopProvider.bind(eventLoop, () -> {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                import ssl
                async def run():
                    await asyncio.open_connection("127.0.0.1", 1, ssl=ssl.create_default_context())
                run()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            Exception exception = assertThrows(Exception.class, () -> stage.toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertTrue(exception.getCause().getMessage().contains("Python ssl.SSLContext is not supported"));
            return null;
            });
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            context.close(true);
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void gracefulShutdownClosesTrackedNettyServers() throws Exception {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        EventLoop eventLoop = eventLoopGroup.next();
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        NettyPythonEventLoopProvider provider = new NettyPythonEventLoopProvider();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(provider));
        try {
            NettyPythonEventLoopProvider.bind(eventLoop, () -> {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                class Hold(asyncio.Protocol):
                    pass
                async def run():
                    server = await asyncio.get_running_loop().create_server(Hold, "127.0.0.1", 0)
                    return server.sockets[0].getsockname()[1]
                run()
                """);

            int port = ((Number) PythonAsyncioRuntime.toCompletionStage(coroutine).toCompletableFuture().get(5, TimeUnit.SECONDS)).intValue();
            provider.shutdownGracefully().toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertThrows(Exception.class, () -> {
                try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
                    socket.getOutputStream().write(1);
                }
            });
            return null;
            });
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            context.close(true);
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void nettyBackedRuntimeRunsConnectAcceptedSocket() throws Exception {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        EventLoop eventLoop = eventLoopGroup.next();
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        CompletableFuture<Channel> acceptedChannel = new CompletableFuture<>();
        Channel[] serverChannel = new Channel[1];
        Channel[] clientChannel = new Channel[1];
        Channel[] accepted = new Channel[1];
        PythonAsyncioRuntime.setEventLoopProviders(List.of(new NettyPythonEventLoopProvider()));
        try {
            NettyPythonEventLoopProvider.bind(eventLoop, () -> {
            serverChannel[0] = new ServerBootstrap()
                .group(eventLoop)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        acceptedChannel.complete(channel);
                    }
                })
                .bind("127.0.0.1", 0)
                .syncUninterruptibly()
                .channel();
            clientChannel[0] = new Bootstrap()
                .group(eventLoop)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                    }
                })
                .connect((InetSocketAddress) serverChannel[0].localAddress())
                .syncUninterruptibly()
                .channel();
            accepted[0] = acceptedChannel.get(5, TimeUnit.SECONDS);
            CompletableFuture<Boolean> ready = new CompletableFuture<>();
            Channel client = clientChannel[0];
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                class Accepted(asyncio.Protocol):
                    def __init__(self, done):
                        self.done = done
                    def connection_made(self, transport):
                        assert transport.get_extra_info("micronaut.netty") is True
                        self.transport = transport
                    def data_received(self, data):
                        self.done.set_result(data.decode())
                async def run(accepted, ready):
                    loop = asyncio.get_running_loop()
                    done = loop.create_future()
                    transport, _ = await loop.connect_accepted_socket(lambda: Accepted(done), accepted)
                    ready.complete(True)
                    try:
                        return await done
                    finally:
                        transport.close()
                run
                """).execute(accepted[0], ready);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);
            assertTrue(ready.get(5, TimeUnit.SECONDS));
            eventLoop.execute(() -> client.writeAndFlush(Unpooled.wrappedBuffer(new byte[] {'o', 'k'}))
                .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE));

            assertEquals("ok", stage.toCompletableFuture().get(5, TimeUnit.SECONDS));
            return null;
            });
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            close(clientChannel[0]);
            close(accepted[0]);
            close(serverChannel[0]);
            context.close(true);
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void nettyBackedRuntimeRunsStreamServerAndClient() throws Exception {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        EventLoop eventLoop = eventLoopGroup.next();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(new NettyPythonEventLoopProvider()));
        PythonAsyncioRuntime.setExecutorService(executorService);
        try {
            NettyPythonEventLoopProvider.bind(eventLoop, () -> {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def handle(reader, writer):
                    assert writer.transport.get_extra_info("micronaut.netty") is True
                    data = await reader.read(2)
                    writer.write(b"hi" + data)
                    await writer.drain()
                    writer.close()
                async def run():
                    server = await asyncio.start_server(handle, "127.0.0.1", 0)
                    reader, writer = await asyncio.open_connection(*server.sockets[0].getsockname())
                    assert writer.transport.get_extra_info("micronaut.netty") is True
                    try:
                        writer.write(b"ok")
                        await writer.drain()
                        return (await reader.read(4)).decode()
                    finally:
                        writer.close()
                        server.close()
                        await server.wait_closed()
                run()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            assertEquals("hiok", stage.toCompletableFuture().get(5, TimeUnit.SECONDS));
            return null;
            });
        } finally {
            PythonAsyncioRuntime.setExecutorService(null);
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            executorService.shutdownNow();
            context.close(true);
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void nettyBackedRuntimeRunsTlsStreamServerAndClient() throws Exception {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        EventLoop eventLoop = eventLoopGroup.next();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        SelfSignedCertificate certificate = new SelfSignedCertificate("localhost");
        PythonAsyncioRuntime.setEventLoopProviders(List.of(new NettyPythonEventLoopProvider()));
        PythonAsyncioRuntime.setExecutorService(executorService);
        try {
            NettyPythonEventLoopProvider.bind(eventLoop, () -> {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def handle(reader, writer):
                    assert writer.transport.get_extra_info("micronaut.netty") is True
                    assert writer.transport.get_extra_info("ssl_object") is not None
                    assert writer.transport.can_write_eof() is False
                    data = await reader.read(2)
                    writer.write(b"tls:" + data)
                    await writer.drain()
                    writer.close()
                async def run(certfile, keyfile):
                    server_ssl = {"certfile": certfile, "keyfile": keyfile}
                    client_ssl = {"trust_all": True}
                    server = await asyncio.start_server(handle, "127.0.0.1", 0, ssl=server_ssl)
                    reader, writer = await asyncio.open_connection(*server.sockets[0].getsockname(), ssl=client_ssl, server_hostname="localhost")
                    assert writer.transport.get_extra_info("micronaut.netty") is True
                    assert writer.transport.get_extra_info("ssl_object") is not None
                    assert writer.transport.get_extra_info("cipher") is not None
                    assert writer.transport.can_write_eof() is False
                    try:
                        writer.write(b"ok")
                        await writer.drain()
                        return (await reader.read(6)).decode()
                    finally:
                        writer.close()
                        server.close()
                        await server.wait_closed()
                run
                """).execute(certificate.certificate().getAbsolutePath(), certificate.privateKey().getAbsolutePath());

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            assertEquals("tls:ok", stage.toCompletableFuture().get(5, TimeUnit.SECONDS));
            return null;
            });
        } finally {
            PythonAsyncioRuntime.setExecutorService(null);
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            executorService.shutdownNow();
            certificate.delete();
            context.close(true);
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void nettyBackedRuntimeRunsUnixServerAndClient() throws Exception {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
        EventLoop eventLoop = eventLoopGroup.next();
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        Path socketPath = Files.createTempDirectory("mn-python-netty").resolve("asyncio.sock");
        Files.deleteIfExists(socketPath);
        PythonAsyncioRuntime.setEventLoopProviders(List.of(new NettyPythonEventLoopProvider()));
        try {
            NettyPythonEventLoopProvider.bind(eventLoop, () -> {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                class Echo(asyncio.Protocol):
                    def connection_made(self, transport):
                        assert transport.get_extra_info("micronaut.netty") is True
                        self.transport = transport
                    def data_received(self, data):
                        self.transport.write(b"uds:" + data)
                        self.transport.close()
                class Client(asyncio.Protocol):
                    def __init__(self, done):
                        self.done = done
                    def connection_made(self, transport):
                        assert transport.get_extra_info("micronaut.netty") is True
                        self.transport = transport
                        transport.write(b"ok")
                    def data_received(self, data):
                        self.done.set_result(data.decode())
                async def run(path):
                    loop = asyncio.get_running_loop()
                    server = await loop.create_unix_server(Echo, path)
                    done = loop.create_future()
                    transport, _ = await loop.create_unix_connection(lambda: Client(done), path)
                    try:
                        return await done
                    finally:
                        transport.close()
                        server.close()
                        await server.wait_closed()
                run
                """).execute(socketPath.toString());

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            assertEquals("uds:ok", stage.toCompletableFuture().get(5, TimeUnit.SECONDS));
            return null;
            });
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            context.close(true);
            eventLoopGroup.shutdownGracefully().syncUninterruptibly();
            Files.deleteIfExists(socketPath);
            Files.deleteIfExists(socketPath.getParent());
        }
    }

    private static void close(Channel channel) {
        if (channel != null && channel.isOpen()) {
            channel.close().addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }
    }
}
