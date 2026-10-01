package io.micronaut.context.python;

import io.micronaut.context.python.netty.NettyPythonEventLoopProvider;
import io.netty.channel.EventLoop;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.ServerSocketChannel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * asyncio semantics the Netty event loop must keep beyond moving bytes: protocol callbacks run inside
 * an execution frame bound to the loop, {@code start_serving=False} defers accepting, write-buffer
 * limits follow asyncio's defaults, datagram transports report {@code connection_lost} once, a server
 * binds every host it is given and the {@code family} argument narrows name resolution.
 */
final class NettyPythonEventLoopSemanticsTest {

    @Test
    void protocolCallbacksRunInsideAnExecutionFrameBoundToTheLoop() throws Exception {
        BooleanSupplier probe = () -> PythonContextRegistry.inExecutionFrame() && PythonAsyncioRuntime.currentEventLoopForContext() != null;
        assertEquals("server=True,client=True", runPython("""
            import asyncio
            class Echo(asyncio.Protocol):
                def __init__(self, probe):
                    self.probe = probe
                def connection_made(self, transport):
                    self.transport = transport
                def data_received(self, data):
                    assert type(data) is bytes, type(data)  # the ByteBuffer handed over arrives as bytes
                    self.transport.write(b"server=" + str(self.probe.getAsBoolean()).encode() + b"," + data)
                    self.transport.close()
            class Client(asyncio.Protocol):
                def __init__(self, done, probe):
                    self.done = done
                    self.probe = probe
                def connection_made(self, transport):
                    transport.write(b"client=" + str(self.probe.getAsBoolean()).encode())
                def data_received(self, data):
                    self.done.set_result(data.decode())
            async def run(probe):
                loop = asyncio.get_running_loop()
                server = await loop.create_server(lambda: Echo(probe), "127.0.0.1", 0)
                done = loop.create_future()
                transport, _ = await loop.create_connection(lambda: Client(done, probe), *server.sockets[0].getsockname())
                try:
                    return await done
                finally:
                    transport.close()
                    server.close()
                    await server.wait_closed()
            run
            """, probe));
    }

    @Test
    void scheduledCallbacksOutlivingTheirCoroutineRunInsideAnExecutionFrame() throws Exception {
        BooleanSupplier probe = () -> PythonContextRegistry.inExecutionFrame() && PythonAsyncioRuntime.currentEventLoopForContext() != null;
        List<Object> results = new CopyOnWriteArrayList<>();
        // the coroutine returns at once; its timer fires after the Java stage has completed
        runPython("""
            import asyncio
            async def run(args):
                probe, results = args[0], args[1]
                loop = asyncio.get_running_loop()
                loop.call_later(0.2, lambda: results.add(probe.getAsBoolean()))
                loop.call_soon(lambda: results.add(probe.getAsBoolean()))
                return "scheduled"
            run
            """, new Object[] {probe, results}, new NettyPythonEventLoopProvider(), () -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (results.size() < 2 && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
            }
        });
        assertEquals(List.of(true, true), results);
    }

    @Test
    void serverFollowsTheAsyncioServerContract() throws Exception {
        assertEquals("serving=True,sockets=1,after_close=0,serving_after=False,forever=cancelled,closed_by_cancel=True", runPython("""
            import asyncio
            class Server(asyncio.Protocol):
                pass
            async def run(_):
                loop = asyncio.get_running_loop()
                async with await loop.create_server(Server, "127.0.0.1", 0, start_serving=False) as server:
                    assert server.get_loop() is loop
                    await server.start_serving()
                    serving = server.is_serving()
                    sockets = len(server.sockets)
                after_close = len(server.sockets)
                serving_after = server.is_serving()
                forever_server = await loop.create_server(Server, "127.0.0.1", 0)
                task = loop.create_task(forever_server.serve_forever())
                await asyncio.sleep(0.1)
                task.cancel()
                try:
                    await task
                    forever = "returned"
                except asyncio.CancelledError:
                    forever = "cancelled"
                return f"serving={serving},sockets={sockets},after_close={after_close},serving_after={serving_after},forever={forever},closed_by_cancel={not forever_server.is_serving()}"
            run
            """, null));
    }

    @Test
    void writabilityDrivesPauseAndResumeWriting() throws Exception {
        assertEquals("paused=1,resumed=1,received=4194304", runPython("""
            import asyncio
            class Sink(asyncio.Protocol):
                def __init__(self, resume):
                    self.resume = resume
                    self.received = 0
                def connection_made(self, transport):
                    self.transport = transport
                    transport.pause_reading()
                    self.resume.add_done_callback(lambda _: transport.resume_reading())
                def data_received(self, data):
                    self.received += len(data)
                    if self.received == 4194304:
                        self.transport.write(b"done")
                        self.transport.close()
            class Source(asyncio.Protocol):
                def __init__(self, paused, done):
                    self.paused = paused
                    self.done = done
                    self.pause_count = 0
                    self.resume_count = 0
                def connection_made(self, transport):
                    transport.set_write_buffer_limits(high=65536, low=16384)
                    transport.write(b"x" * 4194304)
                def pause_writing(self):
                    self.pause_count += 1
                    if not self.paused.done():
                        self.paused.set_result(True)
                def resume_writing(self):
                    self.resume_count += 1
                def data_received(self, data):
                    self.done.set_result(f"paused={self.pause_count},resumed={self.resume_count}")
            async def run(_):
                loop = asyncio.get_running_loop()
                resume = loop.create_future()
                paused = loop.create_future()
                done = loop.create_future()
                sink = Sink(resume)
                server = await loop.create_server(lambda: sink, "127.0.0.1", 0)
                transport, _ = await loop.create_connection(lambda: Source(paused, done), *server.sockets[0].getsockname())
                try:
                    await paused
                    resume.set_result(True)
                    result = await done
                    return f"{result},received={sink.received}"
                finally:
                    transport.close()
                    server.close()
                    await server.wait_closed()
            run
            """, null));
    }

    @Test
    void datagramTransportValidatesAddressesAndReportsProviderShutdown() throws Exception {
        NettyPythonEventLoopProvider provider = new NettyPythonEventLoopProvider();
        Map<String, Object> holder = new ConcurrentHashMap<>();
        assertEquals("unconnected=ValueError,other=ValueError,connected=sent", runPython("""
            import asyncio
            class Endpoint(asyncio.DatagramProtocol):
                def __init__(self):
                    self.lost = []
                def connection_lost(self, exc):
                    self.lost.append(exc)
            async def run(holder):
                loop = asyncio.get_running_loop()
                server, _ = await loop.create_datagram_endpoint(Endpoint, local_addr=("127.0.0.1", 0))
                sockname = server.get_extra_info("sockname")
                client, protocol = await loop.create_datagram_endpoint(Endpoint, remote_addr=sockname)
                try:
                    server.sendto(b"x")
                    unconnected = "sent"
                except ValueError:
                    unconnected = "ValueError"
                try:
                    client.sendto(b"x", ("127.0.0.1", 1))
                    other = "sent"
                except ValueError:
                    other = "ValueError"
                client.sendto(b"x", sockname)
                client.sendto(b"x")
                holder.put("transport", client)
                holder.put("protocol", protocol)
                server.close()
                server.sendto(b"late", sockname)  # dropped: the transport is closing
                return f"unconnected={unconnected},other={other},connected=sent"
            run
            """, holder, provider, () -> {
            provider.shutdownGracefully().toCompletableFuture().get(10, TimeUnit.SECONDS);
            Value transport = Value.asValue(holder.get("transport"));
            Value protocol = Value.asValue(holder.get("protocol"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (protocol.getMember("lost").getArraySize() == 0 && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
            }
            assertEquals(1, protocol.getMember("lost").getArraySize(), "connection_lost after the provider closed the channel");
            assertTrue(transport.invokeMember("is_closing").asBoolean(), "is_closing follows the channel");
        }));
    }

    @Test
    void connectionArgumentsAreHonouredOrRejected() throws Exception {
        assertEquals("numeric=gaierror,proto=NotImplementedError,group=1,fallback=echo:ok,factories=1", runPython("""
            import asyncio
            import socket
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
            factories = []
            async def run(_):
                loop = asyncio.get_running_loop()
                server = await loop.create_server(Echo, "127.0.0.1", 0)
                host, port = server.sockets[0].getsockname()
                def counted(done):
                    def factory():
                        factories.append(1)
                        return Client(done)
                    return factory
                try:
                    try:
                        await loop.create_connection(Client, "localhost", port, flags=socket.AI_NUMERICHOST)
                        numeric = "connected"
                    except socket.gaierror:
                        numeric = "gaierror"
                    try:
                        await loop.create_connection(Client, host, port, proto=socket.IPPROTO_UDP)
                        proto = "connected"
                    except NotImplementedError:
                        proto = "NotImplementedError"
                    closed_server = await loop.create_server(Echo, "127.0.0.1", 0)
                    closed_host, closed_port = closed_server.sockets[0].getsockname()
                    closed_server.close()
                    await closed_server.wait_closed()
                    try:
                        await loop.create_connection(counted(loop.create_future()), closed_host, closed_port, all_errors=True)
                        group = "connected"
                    except ExceptionGroup as failures:
                        group = len(failures.exceptions)
                    # "localhost" resolves to ::1 and 127.0.0.1; only 127.0.0.1 listens, so the other address is skipped
                    done = loop.create_future()
                    transport, _ = await loop.create_connection(counted(done), "localhost", port)
                    try:
                        fallback = await done
                    finally:
                        transport.close()
                    # asyncio calls the factory once, for the connection that succeeded
                    return f"numeric={numeric},proto={proto},group={group},fallback={fallback},factories={len(factories)}"
                finally:
                    server.close()
                    await server.wait_closed()
            run
            """, null));
    }

    @Test
    void aHostlessServerOnAPortInUseFails() throws Exception {
        // the same probe as the loop's: the wildcard family it will bind is the one occupied here
        boolean ipv6;
        try (ServerSocketChannel probe = ServerSocketChannel.open(StandardProtocolFamily.INET6)) {
            ipv6 = probe.isOpen();
        } catch (UnsupportedOperationException e) {
            ipv6 = false;
        }
        try (ServerSocketChannel occupied = ServerSocketChannel.open(ipv6 ? StandardProtocolFamily.INET6 : StandardProtocolFamily.INET)) {
            occupied.bind(new InetSocketAddress(ipv6 ? "::" : "0.0.0.0", 0));
            int port = ((InetSocketAddress) occupied.getLocalAddress()).getPort();
            assertEquals("OSError", runPython("""
                import asyncio
                class Echo(asyncio.Protocol):
                    pass
                async def run(port):
                    loop = asyncio.get_running_loop()
                    try:
                        server = await loop.create_server(Echo, None, port)
                    except OSError:
                        return "OSError"
                    server.close()
                    await server.wait_closed()
                    return "bound"
                run
                """, port));
        }
    }

    @Test
    void theNumericHostFlagAppliesToEveryAddress() throws Exception {
        assertEquals("server:gaierror,local:gaierror,datagram:gaierror,remote:gaierror,literal", runPython("""
            import asyncio
            import socket
            class Echo(asyncio.Protocol):
                pass
            class Datagram(asyncio.DatagramProtocol):
                pass
            async def run(_):
                loop = asyncio.get_running_loop()
                results = []
                try:
                    server = await loop.create_server(Echo, "localhost", 0, flags=socket.AI_NUMERICHOST)
                    server.close()
                    results.append("server")
                except socket.gaierror:
                    results.append("server:gaierror")
                server = await loop.create_server(Echo, "127.0.0.1", 0)
                port = server.sockets[0].getsockname()[1]
                try:
                    try:
                        transport, _ = await loop.create_connection(Echo, "127.0.0.1", port, local_addr=("localhost", 0), flags=socket.AI_NUMERICHOST)
                        transport.close()
                        results.append("local")
                    except socket.gaierror:
                        results.append("local:gaierror")
                finally:
                    server.close()
                    await server.wait_closed()
                try:
                    transport, _ = await loop.create_datagram_endpoint(Datagram, local_addr=("localhost", 0), flags=socket.AI_NUMERICHOST)
                    transport.close()
                    results.append("datagram")
                except socket.gaierror:
                    results.append("datagram:gaierror")
                try:
                    transport, _ = await loop.create_datagram_endpoint(Datagram, remote_addr=("localhost", 9), flags=socket.AI_NUMERICHOST)
                    transport.close()
                    results.append("remote")
                except socket.gaierror:
                    results.append("remote:gaierror")
                # a literal address passes the flag
                server = await loop.create_server(Echo, "127.0.0.1", 0, flags=socket.AI_NUMERICHOST)
                server.close()
                await server.wait_closed()
                results.append("literal")
                return ",".join(results)
            run
            """, null));
    }

    @Test
    void serverBindsEveryAddressOfAName() throws Exception {
        assertEquals("ok", runPython("""
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
            async def run(_):
                loop = asyncio.get_running_loop()
                server = await loop.create_server(Echo, "localhost", 0)
                try:
                    # one listening socket per resolved address; each accepts on its own family
                    families = {len(sock.getsockname()) for sock in server.sockets}
                    for sock in server.sockets:
                        done = loop.create_future()
                        name = sock.getsockname()
                        transport, _ = await loop.create_connection(lambda: Client(done), name[0], name[1])
                        try:
                            assert await done == "echo:ok", name
                        finally:
                            transport.close()
                    assert len(server.sockets) == len(families), server.sockets
                    return "ok"
                finally:
                    server.close()
                    await server.wait_closed()
            run
            """, null));
    }

    @Test
    void invalidArgumentCombinationsAreRejectedUpFront() throws Exception {
        assertEquals("hostname=ValueError,timeout=ValueError,server_timeout=ValueError,datagram=ValueError,unix_hostname=ValueError,accepted_timeout=ValueError,zero_timeout=ValueError,nan_timeout=ValueError,negative_timeout=ValueError,inf_timeout=ValueError,bool_timeout=ValueError,text_timeout=ValueError,ipv6=4,empty_tls=attempted,family_only=0.0.0.0", runPython("""
            import asyncio
            import socket
            class Proto(asyncio.Protocol):
                pass
            class Datagram(asyncio.DatagramProtocol):
                pass
            async def run(_):
                loop = asyncio.get_running_loop()
                results = []
                for label, call in (
                    ("hostname", lambda: loop.create_connection(Proto, "127.0.0.1", 1, server_hostname="example.com")),
                    ("timeout", lambda: loop.create_connection(Proto, "127.0.0.1", 1, ssl_handshake_timeout=1.0)),
                    ("server_timeout", lambda: loop.create_server(Proto, "127.0.0.1", 0, ssl_shutdown_timeout=1.0)),
                    ("datagram", lambda: loop.create_datagram_endpoint(Datagram)),
                    ("unix_hostname", lambda: loop.create_unix_connection(Proto, "/tmp/mn-none.sock", server_hostname="example.com")),
                    ("accepted_timeout", lambda: loop.connect_accepted_socket(Proto, None, ssl_handshake_timeout=1.0)),
                    ("zero_timeout", lambda: loop.create_connection(Proto, "127.0.0.1", 1, ssl={}, ssl_handshake_timeout=0)),
                    ("nan_timeout", lambda: loop.create_connection(Proto, "127.0.0.1", 1, ssl={}, ssl_shutdown_timeout=float("nan"))),
                    ("negative_timeout", lambda: loop.create_connection(Proto, "127.0.0.1", 1, ssl={}, ssl_handshake_timeout=-1)),
                    ("inf_timeout", lambda: loop.create_connection(Proto, "127.0.0.1", 1, ssl={}, ssl_shutdown_timeout=float("inf"))),
                    ("bool_timeout", lambda: loop.create_connection(Proto, "127.0.0.1", 1, ssl={}, ssl_handshake_timeout=True)),
                    ("text_timeout", lambda: loop.create_connection(Proto, "127.0.0.1", 1, ssl={}, ssl_handshake_timeout="1")),
                ):
                    try:
                        await call()
                        results.append(f"{label}=accepted")
                    except ValueError:
                        results.append(f"{label}=ValueError")
                try:
                    transport, _ = await loop.create_datagram_endpoint(Datagram, local_addr=("::1", 0), family=socket.AF_INET6)
                    try:
                        results.append(f"ipv6={len(transport.get_extra_info('sockname'))}")
                    finally:
                        transport.close()
                except OSError:
                    results.append("ipv6=4")  # no IPv6 loopback on this host
                # an empty TLS mapping asks for TLS: the timeout is accepted and TLS setup itself fails, not validation
                try:
                    await loop.create_server(Proto, "127.0.0.1", 0, ssl={}, ssl_shutdown_timeout=1.0)
                    results.append("empty_tls=bound")
                except ValueError:
                    results.append("empty_tls=ValueError")
                except Exception:
                    results.append("empty_tls=attempted")
                # an explicit family without addresses is a wildcard endpoint of that family
                transport, _ = await loop.create_datagram_endpoint(Datagram, family=socket.AF_INET)
                try:
                    results.append(f"family_only={transport.get_extra_info('sockname')[0]}")
                finally:
                    transport.close()
                return ",".join(results)
            run
            """, null));
    }

    @Test
    void callbackFailuresGoToTheExceptionHandler() throws Exception {
        assertEquals("default=survived,custom=ZeroDivisionError,broken_handler=survived", runPython("""
            import asyncio
            async def run(_):
                loop = asyncio.get_running_loop()
                loop.call_soon(lambda: 1 / 0)
                await asyncio.sleep(0.05)
                seen = []
                loop.set_exception_handler(lambda loop, context: seen.append(type(context["exception"]).__name__))
                loop.call_soon(lambda: 1 / 0)
                await asyncio.sleep(0.05)
                custom = ",".join(seen)
                def broken(loop, context):
                    raise RuntimeError("handler failed")
                loop.set_exception_handler(broken)
                loop.call_soon(lambda: 1 / 0)
                await asyncio.sleep(0.05)
                loop.set_exception_handler(None)
                return f"default=survived,custom={custom},broken_handler=survived"
            run
            """, null));
    }

    @Test
    void closingTheServerCancelsServeForeverAndWaitClosedWaitsForClients() throws Exception {
        assertEquals("forever=CancelledError,while_client_open=TimeoutError,after_client_closed=closed,refused=ConnectionRefusedError,loop_close=RuntimeError,stop=RuntimeError,reading=False", runPython("""
            import asyncio
            class Hold(asyncio.Protocol):
                def connection_made(self, transport):
                    self.transport = transport
            class Client(asyncio.Protocol):
                pass
            async def run(_):
                loop = asyncio.get_running_loop()
                server = await loop.create_server(Hold, "127.0.0.1", 0)
                host, port = server.sockets[0].getsockname()
                forever = loop.create_task(server.serve_forever())
                await asyncio.sleep(0.05)
                transport, _ = await loop.create_connection(Client, host, port)
                await asyncio.sleep(0.05)
                # close() from outside cancels serve_forever(), which then awaits wait_closed()
                server.close()
                # wait_closed() waits for the accepted connection too (asyncio 3.12)
                try:
                    await asyncio.wait_for(server.wait_closed(), 0.3)
                    while_open = "closed"
                except (asyncio.TimeoutError, TimeoutError):
                    while_open = "TimeoutError"
                transport.close()
                reading = transport.is_reading()
                try:
                    await forever
                    outcome = "returned"
                except asyncio.CancelledError:
                    outcome = "CancelledError"
                await asyncio.wait_for(server.wait_closed(), 5)
                try:
                    await loop.create_connection(Client, host, port)
                    refused = "connected"
                except ConnectionRefusedError:
                    refused = "ConnectionRefusedError"
                try:
                    loop.close()
                    loop_close = "closed"
                except RuntimeError:
                    loop_close = "RuntimeError"
                try:
                    loop.stop()
                    loop_close += ",stopped"
                except RuntimeError:
                    loop_close += ",stop=RuntimeError"
                return f"forever={outcome},while_client_open={while_open},after_client_closed=closed,refused={refused},loop_close={loop_close},reading={reading}"
            run
            """, null));
    }

    @Test
    void executorWorkersAndStageCompletionsRunInsideAFrame() throws Exception {
        BooleanSupplier probe = () -> PythonContextRegistry.inExecutionFrame() && PythonContextRegistry.activeExecutions() > 0;
        CompletableFuture<String> detached = new CompletableFuture<>();
        List<Object> results = new CopyOnWriteArrayList<>();
        assertEquals("worker=True,completion=True", runPython("""
            import asyncio
            async def run(args):
                probe, detached, results = args
                loop = asyncio.get_running_loop()
                # the worker runs on Micronaut's executor: its guest code is tracked too
                worker = await loop.run_in_executor(None, lambda: probe.getAsBoolean())
                completion = loop.create_future()
                def observe(_):
                    completion.set_result(probe.getAsBoolean())
                pending = loop.run_in_executor(None, lambda: "done")
                pending.add_done_callback(observe)
                await pending
                # a Java stage completed after this coroutine returned still completes inside a frame
                async def wait_detached():
                    await loop._completion_stage_to_future(detached)
                    results.add(probe.getAsBoolean())
                loop.create_task(wait_detached())
                return f"worker={worker},completion={await completion}"
            run
            """, new Object[] {probe, detached, results}, new NettyPythonEventLoopProvider(), () -> {
            detached.complete("late");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (results.isEmpty() && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
            }
            assertEquals(List.of(true), results, "the detached completion ran outside a frame");
        }));
    }

    @Test
    void javaFailuresMapToTheExceptionsAsyncioCodeExpects() throws Exception {
        Object[] failures = {
            new io.netty.channel.ConnectTimeoutException("connect timed out"),
            new io.netty.handler.ssl.SslHandshakeTimeoutException("handshake timed out"),
            new java.net.ConnectException("refused"),
            new java.net.UnknownHostException("nowhere.invalid"),
            new java.io.IOException("broken pipe"),
            new IllegalStateException("not I/O"),
        };
        assertEquals("TimeoutError,TimeoutError,ConnectionRefusedError,gaierror,OSError,RuntimeError", runPython("""
            import asyncio
            import sys
            async def run(failures):
                # the module is installed with the loop; import it after the loop exists
                micronaut_asyncio = sys.modules[type(asyncio.get_running_loop()).__module__]
                names = []
                for failure in failures:
                    exception = micronaut_asyncio._micronaut_java_failure(failure)
                    assert exception.java_exception is failure
                    for base in (TimeoutError, ConnectionRefusedError, OSError, RuntimeError):
                        if isinstance(exception, base):
                            names.append("gaierror" if isinstance(exception, __import__("socket").gaierror) else base.__name__)
                            break
                return ",".join(names)
            run
            """, failures));
    }

    @Test
    void aProtocolWhoseConnectionLostThrowsStillReleasesItsChannel() throws Exception {
        assertEquals("closed", runPython("""
            import asyncio
            class Fragile(asyncio.Protocol):
                def connection_lost(self, exc):
                    raise RuntimeError("connection_lost failed")
            class Client(asyncio.Protocol):
                pass
            async def run(_):
                loop = asyncio.get_running_loop()
                server = await loop.create_server(Fragile, "127.0.0.1", 0)
                transport, _ = await loop.create_connection(Client, *server.sockets[0].getsockname())
                await asyncio.sleep(0.05)
                transport.close()
                server.close()
                # wait_closed() waits for the accepted connection: it only completes if the channel closed
                await asyncio.wait_for(server.wait_closed(), 5)
                return "closed"
            run
            """, null));
    }

    @Test
    void localAddressNamesArePairedWithTheRemotesFamily() throws Exception {
        assertEquals("tcp=echo:ok,udp=echo:ok", runPython("""
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
            class UdpServer(asyncio.DatagramProtocol):
                def connection_made(self, transport):
                    self.transport = transport
                def datagram_received(self, data, addr):
                    assert type(data) is bytes, type(data)  # the ByteBuffer handed over arrives as bytes
                    self.transport.sendto(b"echo:" + data, addr)
            class UdpClient(asyncio.DatagramProtocol):
                def __init__(self, done):
                    self.done = done
                def connection_made(self, transport):
                    transport.sendto(b"ok")
                def datagram_received(self, data, addr):
                    self.done.set_result(data.decode())
            async def run(_):
                loop = asyncio.get_running_loop()
                server = await loop.create_server(Echo, "127.0.0.1", 0)
                host, port = server.sockets[0].getsockname()
                done = loop.create_future()
                # "localhost" may resolve to ::1 first: the IPv4 local candidate must be paired with the IPv4 remote
                transport, _ = await loop.create_connection(lambda: Client(done), host, port, local_addr=("localhost", 0))
                try:
                    tcp = await done
                finally:
                    transport.close()
                    server.close()
                    await server.wait_closed()
                udp_server, _ = await loop.create_datagram_endpoint(UdpServer, local_addr=("127.0.0.1", 0))
                udp_done = loop.create_future()
                udp_client, _ = await loop.create_datagram_endpoint(lambda: UdpClient(udp_done), local_addr=("localhost", 0), remote_addr=udp_server.get_extra_info("sockname"))
                try:
                    udp = await udp_done
                finally:
                    udp_client.close()
                    udp_server.close()
                return f"tcp={tcp},udp={udp}"
            run
            """, null));
    }

    @Test
    void connectedDatagramValidationDistinguishesIpv6Scopes() throws Exception {
        // a two-element tuple carries no scope: against a scoped peer it names a different address
        assertEquals("same=sent,scope=ValueError,two_tuple=ValueError", runPython("""
            import asyncio
            import sys
            class FakeJavaTransport:
                def __init__(self):
                    self.sent = []
                def is_closing(self):
                    return False
                def get_extra_info(self, name, default=None):
                    return ("fe80::1", 5000, 0, 2) if name == "peername" else default
                def sendto(self, data, addr):
                    self.sent.append(addr)
            async def run(_):
                module = sys.modules[type(asyncio.get_running_loop()).__module__]
                transport = module._MicronautNettyDatagramTransport(FakeJavaTransport())
                results = []
                for label, addr in (("same", ("fe80::1", 5000, 0, 2)), ("scope", ("fe80::1", 5000, 0, 3)), ("two_tuple", ("fe80::1", 5000))):
                    try:
                        transport.sendto(b"x", addr)
                        results.append(f"{label}=sent")
                    except ValueError:
                        results.append(f"{label}=ValueError")
                return ",".join(results)
            run
            """, null));
    }

    @Test
    void aServerWithoutAHostListensOnEveryWildcardFamily() throws Exception {
        assertEquals("ok", runPython("""
            import asyncio
            import socket
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
            def ipv6_available():
                try:
                    probe = socket.socket(socket.AF_INET6, socket.SOCK_STREAM)
                    try:
                        probe.bind(("::1", 0))
                    finally:
                        probe.close()
                    return True
                except OSError:
                    return False
            async def run(_):
                loop = asyncio.get_running_loop()
                # None and "" both mean every interface: one dual-stack IPv6 listener when the host has
                # IPv6 (it serves IPv4 too), the IPv4 wildcard otherwise
                for host in (None, ""):
                    server = await loop.create_server(Echo, host, 0)
                    try:
                        names = [sock.getsockname() for sock in server.sockets]
                        assert len(names) == 1, names
                        assert (len(names[0]) == 4) == ipv6_available(), names
                    finally:
                        server.close()
                        await server.wait_closed()
                server = await loop.create_server(Echo, None, 0)
                try:
                    name = server.sockets[0].getsockname()
                    loopbacks = ("::1", "127.0.0.1") if len(name) == 4 else ("127.0.0.1",)
                    for loopback in loopbacks:
                        done = loop.create_future()
                        transport, _ = await loop.create_connection(lambda: Client(done), loopback, name[1])
                        try:
                            assert await done == "echo:ok", (loopback, name)
                        finally:
                            transport.close()
                    return "ok"
                finally:
                    server.close()
                    await server.wait_closed()
            run
            """, null));
    }

    @Test
    void serverDoesNotAcceptBeforeStartServing() throws Exception {
        assertEquals("accepted_early=False,serving=True,accepted=True", runPython("""
            import asyncio
            async def run(_):
                loop = asyncio.get_running_loop()
                accepted = loop.create_future()
                class Server(asyncio.Protocol):
                    def connection_made(self, transport):
                        if not accepted.done():
                            accepted.set_result(True)
                class Client(asyncio.Protocol):
                    pass
                server = await loop.create_server(Server, "127.0.0.1", 0, start_serving=False)
                serving_before = server.is_serving()
                transport, _ = await loop.create_connection(Client, *server.sockets[0].getsockname())
                await asyncio.sleep(0.3)
                accepted_early = accepted.done() or serving_before
                await server.start_serving()
                result = await accepted
                try:
                    return f"accepted_early={accepted_early},serving={server.is_serving()},accepted={result}"
                finally:
                    transport.close()
                    server.close()
                    await server.wait_closed()
            run
            """, null));
    }

    @Test
    void writeBufferLimitsFollowAsyncioDefaults() throws Exception {
        assertEquals("(32768, 65536)|(1024, 4096)|(100, 400)|(16384, 65536)|0", runPython("""
            import asyncio
            class Server(asyncio.Protocol):
                def connection_made(self, transport):
                    self.transport = transport
            class Client(asyncio.Protocol):
                def __init__(self, done):
                    self.done = done
                def connection_made(self, transport):
                    defaults = tuple(transport.get_write_buffer_limits())
                    transport.set_write_buffer_limits(high=4096)
                    after_high = tuple(transport.get_write_buffer_limits())
                    transport.set_write_buffer_limits(low=100)
                    after_low = tuple(transport.get_write_buffer_limits())
                    transport.set_write_buffer_limits()
                    reset = tuple(transport.get_write_buffer_limits())
                    self.done.set_result(f"{defaults}|{after_high}|{after_low}|{reset}|{transport.get_write_buffer_size()}")
            async def run(_):
                loop = asyncio.get_running_loop()
                server = await loop.create_server(Server, "127.0.0.1", 0)
                done = loop.create_future()
                transport, _ = await loop.create_connection(lambda: Client(done), *server.sockets[0].getsockname())
                try:
                    return await done
                finally:
                    transport.close()
                    server.close()
                    await server.wait_closed()
            run
            """, null));
    }

    @Test
    void datagramTransportCloseReportsConnectionLostOnce() throws Exception {
        assertEquals("lost=1,exc=None,closing=True", runPython("""
            import asyncio
            class Endpoint(asyncio.DatagramProtocol):
                def __init__(self, done):
                    self.done = done
                    self.lost = 0
                def connection_lost(self, exc):
                    self.lost += 1
                    if not self.done.done():
                        self.done.set_result(exc)
            async def run(_):
                loop = asyncio.get_running_loop()
                done = loop.create_future()
                transport, protocol = await loop.create_datagram_endpoint(lambda: Endpoint(done), local_addr=("127.0.0.1", 0))
                transport.close()
                exc = await done
                await asyncio.sleep(0.2)
                return f"lost={protocol.lost},exc={exc},closing={transport.is_closing()}"
            run
            """, null));
    }

    @Test
    void serverBindsEveryHostInTheList() throws Exception {
        assertEquals("sockets=2,echo:one,echo:two", runPython("""
            import asyncio
            class Echo(asyncio.Protocol):
                def connection_made(self, transport):
                    self.transport = transport
                def data_received(self, data):
                    self.transport.write(b"echo:" + data)
                    self.transport.close()
            class Client(asyncio.Protocol):
                def __init__(self, done, payload):
                    self.done = done
                    self.payload = payload
                def connection_made(self, transport):
                    transport.write(self.payload)
                def data_received(self, data):
                    self.done.set_result(data.decode())
            async def run(_):
                loop = asyncio.get_running_loop()
                server = await loop.create_server(Echo, ["127.0.0.1", "127.0.0.1"], 0)
                sockets = list(server.sockets)
                results = []
                transports = []
                try:
                    for sock, payload in zip(sockets, (b"one", b"two")):
                        done = loop.create_future()
                        transport, _ = await loop.create_connection(lambda: Client(done, payload), *sock.getsockname())
                        transports.append(transport)
                        results.append(await done)
                    return f"sockets={len(sockets)}," + ",".join(results)
                finally:
                    for transport in transports:
                        transport.close()
                    server.close()
                    await server.wait_closed()
            run
            """, null));
    }

    @Test
    void addressFamilyNarrowsResolution() throws Exception {
        assertEquals("bound=127.0.0.1,inet6=refused,eyeballs=NotImplementedError", runPython("""
            import asyncio
            import socket
            class Server(asyncio.Protocol):
                pass
            class Client(asyncio.Protocol):
                pass
            async def run(_):
                loop = asyncio.get_running_loop()
                server = await loop.create_server(Server, "localhost", 0, family=socket.AF_INET)
                host, port = server.sockets[0].getsockname()
                try:
                    try:
                        await loop.create_connection(Client, "127.0.0.1", port, family=socket.AF_INET6)
                        inet6 = "connected"
                    except Exception as e:
                        inet6 = "refused" if "inet6" in str(e) else repr(e)
                    try:
                        await loop.create_connection(Client, host, port, happy_eyeballs_delay=0.1)
                        eyeballs = "connected"
                    except NotImplementedError:
                        eyeballs = "NotImplementedError"
                    return f"bound={host},inet6={inet6},eyeballs={eyeballs}"
                finally:
                    server.close()
                    await server.wait_closed()
            run
            """, null));
    }

    private static String runPython(String python, Object argument) throws Exception {
        return runPython(python, argument, new NettyPythonEventLoopProvider());
    }

    private static String runPython(String python, Object argument, NettyPythonEventLoopProvider provider) throws Exception {
        return runPython(python, argument, provider, () -> { });
    }

    private interface AfterRun {
        void run() throws Exception;
    }

    /** Runs the coroutine, then {@code afterRun} while the context is still open, then closes everything. */
    private static String runPython(String python, Object argument, NettyPythonEventLoopProvider provider, AfterRun afterRun) throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoop eventLoop = group.next();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(provider));
        PythonAsyncioRuntime.setExecutorService(executorService);
        try {
            String result = provider.call(eventLoop, () -> {
                Value run = context.eval(PYTHON, python).execute(argument);
                CompletionStage<?> stage = PythonAsyncioRuntime.toCompletionStage(run);
                return (String) stage.toCompletableFuture().get(20, TimeUnit.SECONDS);
            });
            afterRun.run();
            return result;
        } finally {
            PythonAsyncioRuntime.setExecutorService(null);
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            executorService.shutdownNow();
            context.close(true);
            group.shutdownGracefully().syncUninterruptibly();
        }
    }
}
