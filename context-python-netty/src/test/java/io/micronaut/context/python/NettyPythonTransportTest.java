package io.micronaut.context.python;

import io.micronaut.context.python.netty.NettyPythonEventLoopProvider;
import io.netty.channel.EventLoop;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.nio.NioIoHandler;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Python networking APIs must work on whatever transport the Netty event loop uses: NIO everywhere,
 * kqueue on macOS, epoll (and io_uring) on Linux.
 */
final class NettyPythonTransportTest {

    private record TransportCase(String name, boolean nativeTransport, IoHandlerFactory ioHandlerFactory) {
    }

    private static List<TransportCase> availableTransports() {
        List<TransportCase> cases = new ArrayList<>();
        cases.add(new TransportCase("nio", false, NioIoHandler.newFactory()));
        if (KQueue.isAvailable()) {
            cases.add(new TransportCase("kqueue", true, KQueueIoHandler.newFactory()));
        }
        if (Epoll.isAvailable()) {
            cases.add(new TransportCase("epoll", true, EpollIoHandler.newFactory()));
        }
        return cases;
    }

    @TestFactory
    List<DynamicTest> tcpEchoRunsOnEveryTransport() {
        return perTransport("tcp", """
            import asyncio
            class Echo(asyncio.Protocol):
                def connection_made(self, transport):
                    self.transport = transport
                def data_received(self, data):
                    self.transport.write(b"echo:" + data)
                    self.transport.close()
            class Client(asyncio.Protocol):
                def __init__(self, done):
                    self.done = done
                def connection_made(self, transport):
                    transport.write(b"ok")
                def data_received(self, data):
                    self.done.set_result(data.decode())
                def connection_lost(self, exc):
                    pass
            async def run(path, reuse_port):
                loop = asyncio.get_running_loop()
                server = await loop.create_server(Echo, "127.0.0.1", 0, reuse_port=reuse_port)
                done = loop.create_future()
                transport, _ = await loop.create_connection(lambda: Client(done), *server.sockets[0].getsockname())
                try:
                    return await done
                finally:
                    transport.close()
                    server.close()
                    await server.wait_closed()
            run
            """);
    }

    @TestFactory
    List<DynamicTest> datagramEchoRunsOnEveryTransport() {
        return perTransport("udp", """
            import asyncio
            class Server(asyncio.DatagramProtocol):
                def __init__(self, done):
                    self.done = done
                def connection_made(self, transport):
                    self.transport = transport
                def datagram_received(self, data, addr):
                    self.transport.sendto(b"echo:" + data, addr)
                def error_received(self, exc):
                    self.done.set_exception(RuntimeError("server error_received: " + repr(exc)))
            class Client(asyncio.DatagramProtocol):
                def __init__(self, done):
                    self.done = done
                def connection_made(self, transport):
                    transport.sendto(b"ok")
                def datagram_received(self, data, addr):
                    self.done.set_result(data.decode())
                def error_received(self, exc):
                    self.done.set_exception(RuntimeError("client error_received: " + repr(exc)))
            async def run(path, reuse_port):
                loop = asyncio.get_running_loop()
                done = loop.create_future()
                server_transport, _ = await loop.create_datagram_endpoint(lambda: Server(done), local_addr=("127.0.0.1", 0))
                sockname = server_transport.get_extra_info("sockname")
                client_transport, _ = await loop.create_datagram_endpoint(lambda: Client(done), remote_addr=sockname)
                try:
                    return await done
                finally:
                    client_transport.close()
                    server_transport.close()
            run
            """);
    }

    @TestFactory
    List<DynamicTest> unixSocketEchoRunsOnEveryTransport() {
        return perTransport("unix", """
            import asyncio
            class Echo(asyncio.Protocol):
                def connection_made(self, transport):
                    self.transport = transport
                def data_received(self, data):
                    self.transport.write(b"echo:" + data)
                    self.transport.close()
            class Client(asyncio.Protocol):
                def __init__(self, done):
                    self.done = done
                def connection_made(self, transport):
                    transport.write(b"ok")
                def data_received(self, data):
                    self.done.set_result(data.decode())
                def connection_lost(self, exc):
                    pass
            async def run(path, reuse_port):
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
            """);
    }

    private static List<DynamicTest> perTransport(String scenario, String python) {
        List<DynamicTest> tests = new ArrayList<>();
        for (TransportCase transportCase : availableTransports()) {
            tests.add(DynamicTest.dynamicTest(scenario + " on " + transportCase.name(), () ->
                assertEquals("echo:ok", runPython(transportCase, python))));
        }
        return tests;
    }

    private static String runPython(TransportCase transportCase, String python) throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transportCase.ioHandlerFactory());
        EventLoop eventLoop = group.next();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        Path socketPath = Path.of("/tmp", "mn-py-" + UUID.randomUUID().toString().substring(0, 8) + ".sock");
        PythonAsyncioRuntime.setEventLoopProviders(List.of(new NettyPythonEventLoopProvider()));
        PythonAsyncioRuntime.setExecutorService(executorService);
        try {
            return NettyPythonEventLoopProvider.bind(eventLoop, () -> {
                // reuse_port needs a native transport; the NIO loop reports that as unsupported
                Value run = context.eval(PYTHON, python).execute(socketPath.toString(), transportCase.nativeTransport());
                CompletionStage<?> stage = PythonAsyncioRuntime.toCompletionStage(run);
                return (String) stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
            });
        } finally {
            PythonAsyncioRuntime.setExecutorService(null);
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            executorService.shutdownNow();
            context.close(true);
            group.shutdownGracefully().syncUninterruptibly();
            Files.deleteIfExists(socketPath);
        }
    }
}
