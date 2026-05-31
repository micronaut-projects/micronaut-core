# Copyright 2017-2026 original authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Micronaut-managed asyncio runtime for Python bridge methods.

This module is framework runtime code loaded from the GraalPy virtual file
system by :class:`PythonAsyncioRuntime`. It intentionally implements a narrow
asyncio event loop instead of delegating to a selector loop: Micronaut owns the
request-processing thread, and Netty owns the event loop that drives HTTP I/O.
The classes below translate Python asyncio scheduling, timers, stream
transports, datagram transports, and Java ``CompletionStage`` bridging into
operations that can be driven by a Micronaut-provided Java event-loop facade.

Maintainer notes:
* Keep Java host calls small and explicit. Public Python behavior should look
  like normal asyncio, but the implementation must avoid blocking a Netty event
  loop.
* Prefer adding support behind capability checks on ``self._java_loop``. The
  base ``micronaut-context-python`` module provides socket fallbacks;
  ``micronaut-context-python-netty`` adds Netty-native factories.
* Do not expose raw Netty channels or handlers from this module. Java transport
  facades provide stable socket-like extras for Python callers.
* Keep unsupported APIs deterministic. Raising ``NotImplementedError`` is
  preferable to silently taking a blocking or semantically incomplete path.

Implementation map:
* ``_MicronautAsyncioHandle`` and ``_MicronautAsyncioTimerHandle`` preserve
  asyncio callback/timer cancellation state while Java owns execution timing.
* ``_MicronautSocketTransport``, ``_MicronautDatagramTransport``, and
  ``_MicronautServer`` are compatibility fallbacks used when Netty cannot own
  the socket.
* ``_MicronautNettyServer`` adapts Java's Netty server facade to the Python
  ``asyncio.Server`` contract without exposing Netty implementation objects.
* ``_MicronautAsyncioEventLoop`` is the central event-loop adapter. It prefers
  Java/Netty capabilities and falls back only for socket APIs that can be
  driven without blocking the caller.
* The ``__micronaut_*`` functions at the bottom are the stable Java entry
  points used by ``PythonAsyncioRuntime``.
"""

import asyncio
import errno
import inspect
import os
import select
import socket
import ssl as _micronaut_ssl
import traceback
import java

from asyncio import events
from asyncio import futures
from asyncio import tasks
from asyncio import transports

class _MicronautAsyncioHandle:
    """Minimal callback handle used by the Micronaut-managed event loop.

    The Java event-loop facade owns the actual scheduling. This object only
    stores Python callback state and cancellation state so callback execution
    keeps asyncio's expected ``Handle`` shape without depending on CPython's
    private selector-loop internals.
    """

    def __init__(self, callback, args, context=None):
        self._callback = callback
        self._args = args
        self._context = context
        self._cancelled = False

    def cancel(self):
        self._cancelled = True

    def cancelled(self):
        return self._cancelled

    def _run(self):
        if self._cancelled:
            return
        if self._context is None:
            self._callback(*self._args)
        else:
            self._context.run(self._callback, *self._args)

class _MicronautAsyncioTimerHandle(_MicronautAsyncioHandle):
    """Timer variant that can cancel the backing Java scheduled future.

    ``call_later`` and ``call_at`` schedule through ``PythonEventLoop`` on the
    Java side. Keeping the Java future here lets Python cancellation propagate
    immediately instead of waiting for a no-op callback to fire later.
    """

    def __init__(self, when, callback, args, context=None):
        super().__init__(callback, args, context)
        self._when = when
        self._scheduled_future = None

    def when(self):
        return self._when

    def cancel(self):
        super().cancel()
        if self._scheduled_future is not None:
            self._scheduled_future.cancel(False)

class _MicronautSocketTransport(transports.Transport):
    """Socket fallback stream transport for non-Netty paths.

    Netty-backed transports are Java host objects supplied by
    ``micronaut-context-python-netty``. This transport is used only when Python
    code supplies an ordinary socket or requests an option/address family that
    the Java event loop does not handle natively. It intentionally uses the
    loop's socket coroutine helpers so operations are retried from the managed
    event loop instead of blocking the caller.
    """

    def __init__(self, loop, sock, protocol):
        self._loop = loop
        self._sock = sock
        self._protocol = protocol
        self._closing = False
        self._reading = True
        self._read_task = None
        self._extra = {"socket": sock}
        try:
            self._extra["sockname"] = sock.getsockname()
        except OSError:
            pass
        try:
            self._extra["peername"] = sock.getpeername()
        except OSError:
            pass
        protocol.connection_made(self)
        self._start_reading()

    def _start_reading(self):
        if not self._closing and self._reading and self._read_task is None:
            self._read_task = self._loop.create_task(self._read_loop())

    async def _read_loop(self):
        try:
            while not self._closing and self._reading:
                data = await self._loop.sock_recv(self._sock, 65536)
                if data:
                    self._protocol.data_received(data)
                else:
                    keep_open = False
                    if hasattr(self._protocol, "eof_received"):
                        keep_open = bool(self._protocol.eof_received())
                    if not keep_open:
                        self.close()
                    break
        except asyncio.CancelledError:
            raise
        except BaseException as exc:
            self._force_close(exc)
        finally:
            self._read_task = None

    def get_extra_info(self, name, default=None):
        return self._extra.get(name, default)

    def is_closing(self):
        return self._closing

    def close(self):
        if self._closing:
            return
        self._closing = True
        task = self._read_task
        if task is not None:
            task.cancel()
        try:
            self._sock.close()
        finally:
            self._loop.call_soon(self._protocol.connection_lost, None)

    def abort(self):
        self.close()

    def _force_close(self, exc):
        if self._closing:
            return
        self._closing = True
        try:
            self._sock.close()
        finally:
            self._loop.call_soon(self._protocol.connection_lost, exc)

    def write(self, data):
        if self._closing:
            return
        view = memoryview(data)
        try:
            sent = self._sock.send(view)
        except (BlockingIOError, InterruptedError):
            sent = 0
        if sent < len(view):
            self._loop.create_task(self._loop.sock_sendall(self._sock, view[sent:]))

    def writelines(self, list_of_data):
        self.write(b"".join(list_of_data))

    def can_write_eof(self):
        return True

    def write_eof(self):
        try:
            self._sock.shutdown(socket.SHUT_WR)
        except OSError:
            pass

    def get_write_buffer_size(self):
        return 0

    def get_write_buffer_limits(self):
        return (0, 0)

    def set_write_buffer_limits(self, high=None, low=None):
        pass

    def pause_reading(self):
        self._reading = False

    def resume_reading(self):
        if self._closing or self._reading:
            return
        self._reading = True
        self._start_reading()

    def set_protocol(self, protocol):
        self._protocol = protocol

    def get_protocol(self):
        return self._protocol

class _MicronautDatagramTransport(transports.DatagramTransport):
    """Socket fallback datagram transport.

    The Netty module provides a Java datagram transport for normal UDP usage.
    This Python implementation exists for supplied sockets and compatibility
    cases. Error handling mirrors asyncio's datagram protocol contract:
    protocol ``error_received`` is preferred, otherwise the loop exception
    handler receives contextual failure details.
    """

    def __init__(self, loop, sock, protocol):
        self._loop = loop
        self._sock = sock
        self._protocol = protocol
        self._closing = False
        self._read_task = None
        self._extra = {"socket": sock}
        try:
            self._extra["sockname"] = sock.getsockname()
        except OSError:
            pass
        try:
            self._extra["peername"] = sock.getpeername()
        except OSError:
            pass
        protocol.connection_made(self)
        self._start_reading()

    def _start_reading(self):
        if not self._closing and self._read_task is None:
            self._read_task = self._loop.create_task(self._read_loop())

    async def _read_loop(self):
        try:
            while not self._closing:
                data, address = await self._loop.sock_recvfrom(self._sock, 65536)
                self._protocol.datagram_received(data, address)
        except asyncio.CancelledError:
            raise
        except OSError as exc:
            if not self._closing:
                if hasattr(self._protocol, "error_received"):
                    self._protocol.error_received(exc)
                else:
                    self._loop.call_exception_handler({"message": "Exception in Micronaut asyncio datagram transport", "exception": exc, "transport": self})
        finally:
            self._read_task = None

    def sendto(self, data, addr=None):
        if self._closing:
            return
        try:
            if addr is None:
                self._sock.send(data)
            else:
                self._sock.sendto(data, addr)
        except (BlockingIOError, InterruptedError):
            self._loop.create_task(self._send_later(data, addr))
        except OSError as exc:
            if hasattr(self._protocol, "error_received"):
                self._protocol.error_received(exc)
            else:
                raise

    async def _send_later(self, data, addr):
        try:
            if addr is None:
                await self._loop._retry_socket_call(lambda: self._sock.send(data))
            else:
                await self._loop.sock_sendto(self._sock, data, addr)
        except OSError as exc:
            if hasattr(self._protocol, "error_received"):
                self._protocol.error_received(exc)
            else:
                self._loop.call_exception_handler({"message": "Exception in Micronaut asyncio datagram send", "exception": exc, "transport": self})

    def get_extra_info(self, name, default=None):
        return self._extra.get(name, default)

    def is_closing(self):
        return self._closing

    def close(self):
        if self._closing:
            return
        self._closing = True
        task = self._read_task
        if task is not None:
            task.cancel()
        try:
            self._sock.close()
        finally:
            self._loop.call_soon(self._protocol.connection_lost, None)

    def abort(self):
        self.close()

class _MicronautServer:
    """Socket fallback server for accepted stream connections.

    Netty-backed servers are represented by ``_MicronautNettyServer`` below.
    This fallback accepts with ``sock_accept`` and wraps accepted sockets in
    ``_MicronautSocketTransport``. It is intentionally small: lifecycle,
    ``sockets``, ``start_serving``, ``serve_forever``, and ``wait_closed`` are
    implemented because those are the parts used by asyncio stream helpers.
    """

    def __init__(self, loop, sockets, protocol_factory, start_serving):
        self._loop = loop
        self._sockets = tuple(sockets)
        self._protocol_factory = protocol_factory
        self._closing = False
        self._tasks = []
        self._closed = loop.create_future()
        if start_serving:
            self.start_serving()

    @property
    def sockets(self):
        return self._sockets

    def start_serving(self):
        if self._closing or self._tasks:
            return
        for sock in self._sockets:
            self._tasks.append(self._loop.create_task(self._accept_loop(sock)))

    async def serve_forever(self):
        self.start_serving()
        await self._closed

    async def _accept_loop(self, sock):
        try:
            while not self._closing:
                try:
                    accepted, _ = await self._loop.sock_accept(sock)
                except OSError:
                    if not self._closing:
                        raise
                    return
                _MicronautSocketTransport(self._loop, accepted, self._protocol_factory())
        except asyncio.CancelledError:
            raise
        except BaseException as exc:
            self._loop.call_exception_handler({"message": "Exception in Micronaut asyncio server", "exception": exc, "server": self})

    def close(self):
        if self._closing:
            return
        self._closing = True
        for task in self._tasks:
            task.cancel()
        for sock in self._sockets:
            try:
                sock.close()
            except OSError:
                pass
        if not self._closed.done():
            self._closed.set_result(None)

    async def wait_closed(self):
        await self._closed

    def is_serving(self):
        return not self._closing and bool(self._tasks)

class _MicronautNettyServer:
    """Python facade over a Java Netty-backed asyncio server.

    The Java object owns the channel and graceful close future. This facade
    adapts that object to Python's ``asyncio.Server`` shape while preserving the
    invariant that no raw Netty channel leaks into application code.
    """

    def __init__(self, loop, java_server):
        self._loop = loop
        self._java_server = java_server

    @property
    def sockets(self):
        return self._java_server.sockets()

    def start_serving(self):
        self._java_server.startServing()

    async def serve_forever(self):
        self.start_serving()
        await self.wait_closed()

    def close(self):
        self._java_server.close()

    async def wait_closed(self):
        await self._loop._completion_stage_to_future(self._java_server.waitClosed())

    def is_serving(self):
        return self._java_server.isServing()

class _MicronautAsyncioEventLoop(asyncio.AbstractEventLoop):
    """Asyncio event loop driven by a Micronaut ``PythonEventLoop`` facade.

    This is not a general-purpose selector loop. It is a bridge that lets
    coroutine code use the subset of asyncio that Micronaut can safely drive
    from Java. Methods are grouped into scheduling, future/task creation,
    executor handoff, networking, socket helpers, and deterministic unsupported
    APIs. When ``self._java_loop`` exposes Netty factory methods, networking
    operations take the native Netty path; otherwise they fall back to the
    socket transports above.
    """

    def __init__(self, java_loop, time_unit, executor_adapter=None):
        self._java_loop = java_loop
        self._time_unit = time_unit
        self._executor_adapter = executor_adapter
        self._closed = False
        self._debug = False
        self._exception_handler = None

    def _run_handle(self, handle):
        old_loop = events._get_running_loop()
        events._set_running_loop(self)
        try:
            handle._run()
        except BaseException as exc:
            self.call_exception_handler({"message": "Exception in Micronaut asyncio callback", "exception": exc, "handle": handle})
        finally:
            events._set_running_loop(old_loop)

    def run_forever(self):
        raise RuntimeError("Micronaut-managed asyncio loops are driven by the Netty EventLoop")

    def run_until_complete(self, future):
        raise RuntimeError("Micronaut-managed asyncio loops cannot be blocked with run_until_complete")

    def stop(self):
        pass

    def is_running(self):
        return True

    def is_closed(self):
        return self._closed

    def close(self):
        self._closed = True

    def time(self):
        return self._java_loop.time()

    def call_soon(self, callback, *args, context=None):
        self._check_closed()
        handle = _MicronautAsyncioHandle(callback, args, context)
        self._java_loop.execute(lambda: self._run_handle(handle))
        return handle

    def call_soon_threadsafe(self, callback, *args, context=None):
        return self.call_soon(callback, *args, context=context)

    def call_later(self, delay, callback, *args, context=None):
        return self.call_at(self.time() + delay, callback, *args, context=context)

    def call_at(self, when, callback, *args, context=None):
        self._check_closed()
        handle = _MicronautAsyncioTimerHandle(when, callback, args, context)
        delay = max(0.0, when - self.time())
        handle._scheduled_future = self._java_loop.schedule(lambda: self._run_handle(handle), int(delay * 1000000000), self._time_unit)
        return handle

    def create_future(self):
        return futures.Future(loop=self)

    def create_task(self, coro, *, name=None, context=None, eager_start=None, **kwargs):
        if eager_start is not None:
            raise NotImplementedError("asyncio eager task execution is not supported by the Micronaut Netty event loop")
        if kwargs:
            unsupported = ", ".join(sorted(kwargs))
            raise NotImplementedError(f"asyncio task keyword arguments are not supported by the Micronaut Netty event loop: {unsupported}")
        return tasks.Task(coro, loop=self, name=name, context=context)

    def get_debug(self):
        return self._debug

    def set_debug(self, enabled):
        self._debug = bool(enabled)

    def get_exception_handler(self):
        return self._exception_handler

    def set_exception_handler(self, handler):
        self._exception_handler = handler

    def default_exception_handler(self, context):
        exception = context.get("exception")
        if exception is not None:
            raise exception

    def call_exception_handler(self, context):
        if self._exception_handler is None:
            self.default_exception_handler(context)
        else:
            self._exception_handler(self, context)

    async def shutdown_asyncgens(self):
        pass

    async def shutdown_default_executor(self, timeout=None):
        pass

    def _check_closed(self):
        if self._closed:
            raise RuntimeError("Event loop is closed")

    def _unsupported(self, name):
        raise NotImplementedError(f"asyncio event-loop API [{name}] is not supported by the Micronaut Netty event loop")

    def _check_ssl(self, ssl):
        if isinstance(ssl, _micronaut_ssl.SSLContext):
            raise NotImplementedError("Python ssl.SSLContext is not supported by the Micronaut Netty asyncio event loop. Use ssl=True or an ssl={...} mapping.")

    def _java_ssl(self, ssl):
        """Convert the supported Python TLS mapping into Java collections.

        The Java side understands ``True`` and a constrained ``dict`` shape.
        A Python ``ssl.SSLContext`` is deliberately rejected in ``_check_ssl``
        because inspecting GraalPy SSL internals would couple this bridge to
        implementation details outside Micronaut's control.
        """

        self._check_ssl(ssl)
        if isinstance(ssl, dict):
            HashMap = java.type("java.util.HashMap")
            ArrayList = java.type("java.util.ArrayList")
            java_ssl = HashMap()
            for key, value in ssl.items():
                if isinstance(value, (list, tuple)):
                    java_value = ArrayList()
                    for item in value:
                        java_value.add(item)
                else:
                    java_value = value
                java_ssl.put(key, java_value)
            return java_ssl
        return ssl

    def _completion_stage_to_future(self, stage):
        """Adapt a Java ``CompletionStage`` returned by a facade to asyncio."""

        future = self.create_future()
        if hasattr(stage, "cancel"):
            future.add_done_callback(lambda completed: stage.cancel(False) if completed.cancelled() else None)
        def complete(value, throwable):
            if future.cancelled():
                return
            def apply_completion():
                if future.cancelled():
                    return
                if throwable is None:
                    future.set_result(value)
                else:
                    future.set_exception(RuntimeError(str(throwable)))
            if throwable is None:
                self.call_soon_threadsafe(apply_completion)
            else:
                self.call_soon_threadsafe(apply_completion)
        stage.whenComplete(complete)
        return future

    def run_in_executor(self, executor, func, *args):
        self._check_closed()
        if executor is not None:
            self._unsupported("run_in_executor with a custom executor")
        if self._executor_adapter is None:
            self._unsupported("run_in_executor")
        future = self.create_future()
        self._executor_adapter.run(future, lambda: func(*args), self._java_loop)
        return future

    def _retry_socket_call(self, callback):
        """Retry a nonblocking socket operation from the managed event loop."""

        future = self.create_future()
        def attempt():
            if future.cancelled():
                return
            try:
                result = callback()
            except (BlockingIOError, InterruptedError):
                self.call_later(0.001, attempt)
            except BaseException as exc:
                future.set_exception(exc)
            else:
                future.set_result(result)
        self.call_soon(attempt)
        return future

    def _writable_socket(self, sock):
        """Raise until a nonblocking connect has completed successfully."""

        _, writable, _ = select.select([], [sock], [], 0)
        if not writable:
            raise BlockingIOError()
        error = sock.getsockopt(socket.SOL_SOCKET, socket.SO_ERROR)
        if error:
            raise OSError(error, os.strerror(error))

    async def getaddrinfo(self, host, port, *, family=0, type=0, proto=0, flags=0):
        return await self.run_in_executor(None, lambda: socket.getaddrinfo(host, port, family, type, proto, flags))

    async def getnameinfo(self, sockaddr, flags=0):
        return await self.run_in_executor(None, lambda: socket.getnameinfo(sockaddr, flags))

    async def create_connection(self, protocol_factory, host=None, port=None, *, ssl=None, family=0, proto=0, flags=0, sock=None, local_addr=None, server_hostname=None, ssl_handshake_timeout=None, ssl_shutdown_timeout=None, happy_eyeballs_delay=None, interleave=None, all_errors=False):
        ssl = self._java_ssl(ssl)
        if sock is None and family in (0, socket.AF_INET) and proto == 0 and flags == 0 and host is not None and port is not None:
            try:
                netty_factory = self._java_loop.createConnection
            except AttributeError:
                netty_factory = None
            if netty_factory is not None:
                local_host = None
                local_port = -1
                if local_addr is not None:
                    local_host = local_addr[0]
                    local_port = local_addr[1]
                connection = await self._completion_stage_to_future(netty_factory(protocol_factory, host, int(port), local_host, local_port, ssl, server_hostname, ssl_handshake_timeout, ssl_shutdown_timeout))
                return connection[0], connection[1]
        if sock is None:
            infos = await self.getaddrinfo(host, port, family=family, type=socket.SOCK_STREAM, proto=proto, flags=flags)
            if not infos:
                raise OSError("getaddrinfo returned an empty list")
            last_error = None
            for family, type_, proto_, _, address in infos:
                sock = socket.socket(family, type_, proto_)
                sock.setblocking(False)
                try:
                    if local_addr is not None:
                        sock.bind(local_addr)
                    await self.sock_connect(sock, address)
                    break
                except OSError as exc:
                    last_error = exc
                    sock.close()
                    sock = None
            if sock is None:
                raise last_error if last_error is not None else OSError("connection failed")
        else:
            sock.setblocking(False)
        protocol = protocol_factory()
        transport = _MicronautSocketTransport(self, sock, protocol)
        return transport, protocol

    async def create_server(self, protocol_factory, host=None, port=None, *, family=socket.AF_UNSPEC, flags=socket.AI_PASSIVE, sock=None, backlog=100, ssl=None, reuse_address=None, reuse_port=None, keep_alive=None, ssl_handshake_timeout=None, ssl_shutdown_timeout=None, start_serving=True):
        ssl = self._java_ssl(ssl)
        if sock is None and family in (socket.AF_UNSPEC, socket.AF_INET) and flags in (0, socket.AI_PASSIVE) and keep_alive is None:
            try:
                netty_factory = self._java_loop.createServer
            except AttributeError:
                netty_factory = None
            if netty_factory is not None:
                if isinstance(host, (list, tuple)):
                    host = host[0] if host else None
                java_server = await self._completion_stage_to_future(netty_factory(protocol_factory, host, int(port or 0), int(backlog), reuse_address is not False, bool(reuse_port), bool(start_serving), ssl, ssl_handshake_timeout, ssl_shutdown_timeout))
                return _MicronautNettyServer(self, java_server)
        sockets = []
        if sock is not None:
            sock.setblocking(False)
            sockets.append(sock)
        else:
            infos = await self.getaddrinfo(host, port, family=family, type=socket.SOCK_STREAM, flags=flags)
            bound = set()
            for family, type_, proto_, _, address in infos:
                if address in bound:
                    continue
                bound.add(address)
                server_sock = socket.socket(family, type_, proto_)
                try:
                    if reuse_address is not False:
                        server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                    if reuse_port:
                        server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
                    server_sock.bind(address)
                    server_sock.listen(backlog)
                    server_sock.setblocking(False)
                    sockets.append(server_sock)
                except BaseException:
                    server_sock.close()
                    raise
        return _MicronautServer(self, sockets, protocol_factory, start_serving)

    async def create_unix_connection(self, protocol_factory, path=None, *, ssl=None, sock=None, server_hostname=None, ssl_handshake_timeout=None, ssl_shutdown_timeout=None):
        ssl = self._java_ssl(ssl)
        if sock is None and path is not None:
            try:
                netty_factory = self._java_loop.createUnixConnection
            except AttributeError:
                netty_factory = None
            if netty_factory is not None:
                connection = await self._completion_stage_to_future(netty_factory(protocol_factory, str(path), ssl, server_hostname, ssl_handshake_timeout, ssl_shutdown_timeout))
                return connection[0], connection[1]
        self._unsupported("create_unix_connection")

    async def create_unix_server(self, protocol_factory, path=None, *, sock=None, backlog=100, ssl=None, ssl_handshake_timeout=None, ssl_shutdown_timeout=None, start_serving=True):
        ssl = self._java_ssl(ssl)
        if sock is None and path is not None:
            try:
                netty_factory = self._java_loop.createUnixServer
            except AttributeError:
                netty_factory = None
            if netty_factory is not None:
                java_server = await self._completion_stage_to_future(netty_factory(protocol_factory, str(path), int(backlog), bool(start_serving), ssl, ssl_handshake_timeout, ssl_shutdown_timeout))
                return _MicronautNettyServer(self, java_server)
        self._unsupported("create_unix_server")

    async def connect_accepted_socket(self, protocol_factory, sock, *, ssl=None, ssl_handshake_timeout=None, ssl_shutdown_timeout=None):
        ssl = self._java_ssl(ssl)
        try:
            netty_factory = self._java_loop.connectAcceptedSocket
        except AttributeError:
            netty_factory = None
        if netty_factory is not None:
            connection = await self._completion_stage_to_future(netty_factory(protocol_factory, sock, ssl, ssl_handshake_timeout, ssl_shutdown_timeout))
            if connection is not None:
                return connection[0], connection[1]
        sock.setblocking(False)
        protocol = protocol_factory()
        transport = _MicronautSocketTransport(self, sock, protocol)
        return transport, protocol

    async def create_datagram_endpoint(self, protocol_factory, local_addr=None, remote_addr=None, *, family=0, proto=0, flags=0, reuse_port=None, allow_broadcast=None, sock=None):
        if sock is None and family in (0, socket.AF_INET) and proto in (0, socket.IPPROTO_UDP) and flags == 0:
            try:
                netty_factory = self._java_loop.createDatagramEndpoint
            except AttributeError:
                netty_factory = None
            if netty_factory is not None:
                local_host = None
                local_port = -1
                remote_host = None
                remote_port = -1
                if local_addr is not None:
                    local_host = local_addr[0]
                    local_port = local_addr[1]
                if remote_addr is not None:
                    remote_host = remote_addr[0]
                    remote_port = remote_addr[1]
                endpoint = await self._completion_stage_to_future(netty_factory(protocol_factory, local_host, local_port, remote_host, remote_port, bool(allow_broadcast), bool(reuse_port)))
                return endpoint[0], endpoint[1]
        if sock is not None and (local_addr is not None or remote_addr is not None):
            raise ValueError("socket modifier keyword arguments can not be used when sock is specified")
        if sock is None:
            lookup_host = "0.0.0.0"
            lookup_port = 0
            lookup_flags = flags
            if remote_addr is not None:
                lookup_host, lookup_port = remote_addr
            elif local_addr is not None:
                lookup_host, lookup_port = local_addr
                lookup_flags |= socket.AI_PASSIVE
            elif family == 0:
                family = socket.AF_INET
            infos = await self.getaddrinfo(lookup_host, lookup_port, family=family, type=socket.SOCK_DGRAM, proto=proto, flags=lookup_flags)
            if not infos:
                raise OSError("getaddrinfo returned an empty list")
            last_error = None
            for family, type_, proto_, _, address in infos:
                sock = socket.socket(family, type_, proto_)
                try:
                    if reuse_port:
                        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
                    if allow_broadcast:
                        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
                    if local_addr is not None:
                        sock.bind(address if remote_addr is None else local_addr)
                    if remote_addr is not None:
                        sock.connect(address)
                    sock.setblocking(False)
                    break
                except OSError as exc:
                    last_error = exc
                    sock.close()
                    sock = None
            if sock is None:
                raise last_error if last_error is not None else OSError("datagram endpoint failed")
        else:
            sock.setblocking(False)
        protocol = protocol_factory()
        transport = _MicronautDatagramTransport(self, sock, protocol)
        return transport, protocol

    def sendfile(self, *args, **kwargs):
        self._unsupported("sendfile")

    def start_tls(self, *args, **kwargs):
        self._unsupported("start_tls")

    def add_reader(self, *args):
        self._unsupported("add_reader")

    def remove_reader(self, *args):
        self._unsupported("remove_reader")

    def add_writer(self, *args):
        self._unsupported("add_writer")

    def remove_writer(self, *args):
        self._unsupported("remove_writer")

    async def sock_recv(self, sock, n):
        return await self._retry_socket_call(lambda: sock.recv(n))

    async def sock_recv_into(self, sock, buf):
        return await self._retry_socket_call(lambda: sock.recv_into(buf))

    async def sock_recvfrom(self, sock, bufsize):
        return await self._retry_socket_call(lambda: sock.recvfrom(bufsize))

    async def sock_recvfrom_into(self, sock, buf, nbytes=0):
        if nbytes and nbytes > 0:
            return await self._retry_socket_call(lambda: sock.recvfrom_into(buf, nbytes))
        return await self._retry_socket_call(lambda: sock.recvfrom_into(buf))

    async def sock_sendall(self, sock, data):
        view = memoryview(data)
        total_sent = 0
        while total_sent < len(view):
            sent = await self._retry_socket_call(lambda: sock.send(view[total_sent:]))
            if sent == 0:
                raise RuntimeError("socket connection broken")
            total_sent += sent

    async def sock_sendto(self, sock, data, address):
        return await self._retry_socket_call(lambda: sock.sendto(data, address))

    async def sock_connect(self, sock, address):
        try:
            sock.connect(address)
            return None
        except (BlockingIOError, InterruptedError):
            pass
        except OSError as exc:
            if exc.errno not in (errno.EINPROGRESS, errno.EALREADY, errno.EWOULDBLOCK):
                raise
        return await self._retry_socket_call(lambda: self._writable_socket(sock))

    async def sock_accept(self, sock):
        conn, address = await self._retry_socket_call(lambda: sock.accept())
        conn.setblocking(False)
        return conn, address

    def subprocess_exec(self, *args, **kwargs):
        self._unsupported("subprocess_exec")

    def subprocess_shell(self, *args, **kwargs):
        self._unsupported("subprocess_shell")

_micronaut_asyncio_loops = {}

def __micronaut_install_asyncio_event_loop(java_loop, time_unit, executor_adapter=None):
    """Install or update the Micronaut-managed loop for one Java event loop.

    Java ``PythonEventLoop`` instances are stable identity keys. Reusing the
    Python loop object preserves tasks/futures created during a request while
    still allowing the blocking executor adapter to be refreshed between
    bridge invocations.
    """

    loop = _micronaut_asyncio_loops.get(java_loop)
    if loop is None:
        loop = _MicronautAsyncioEventLoop(java_loop, time_unit, executor_adapter)
        _micronaut_asyncio_loops[java_loop] = loop
    else:
        loop._executor_adapter = executor_adapter
    asyncio.set_event_loop(loop)
    events._set_running_loop(loop)
    return loop

def __micronaut_asyncio_to_completion_stage(awaitable, java_future, exception_completer, java_loop, time_unit, executor_adapter=None):
    """Drive a Python awaitable and complete the Java bridge future.

    This is the main Java entry point used by ``PythonAsyncioRuntime``. It
    handles three cases:
    * Non-awaitable values complete the Java future immediately.
    * Micronaut-managed execution installs this module's event loop and creates
      an asyncio task on it.
    * Existing Python event-loop execution delegates to ``asyncio.ensure_future``.

    Cancellation is bidirectional where possible: Java cancellation schedules a
    Python task cancellation, and Python task cancellation cancels the Java
    future. Exceptions are routed through Java's ``ExceptionCompleter`` so the
    bridge keeps existing exception wrapping semantics.
    """

    if not inspect.isawaitable(awaitable):
        java_future.complete(awaitable)
        return java_future
    if java_loop is not None:
        loop = __micronaut_install_asyncio_event_loop(java_loop, time_unit, executor_adapter)
        task = loop.create_task(awaitable)
        java_future.setCancelCallback(lambda: loop.call_soon_threadsafe(task.cancel))
        def done(completed):
            try:
                if completed.cancelled():
                    java_future.cancel(False)
                    return
                exception = completed.exception()
                if exception is not None:
                    exception_completer.completeExceptionally(java_future, exception.__class__.__name__, str(exception))
                    return
                java_future.complete(completed.result())
            except BaseException as exc:
                exception_completer.completeExceptionally(java_future, exc.__class__.__name__, "".join(traceback.format_exception(exc)))
        task.add_done_callback(done)
        return java_future
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        try:
            java_future.complete(asyncio.get_event_loop().run_until_complete(awaitable))
        except BaseException as exc:
            exception_completer.completeExceptionally(java_future, exc.__class__.__name__, str(exc))
        return java_future
    task = asyncio.ensure_future(awaitable, loop=loop)
    java_future.setCancelCallback(lambda: loop.call_soon_threadsafe(task.cancel))
    def done(completed):
        try:
            if completed.cancelled():
                java_future.cancel(False)
                return
            exception = completed.exception()
            if exception is not None:
                exception_completer.completeExceptionally(java_future, exception.__class__.__name__, str(exception))
                return
            java_future.complete(completed.result())
        except BaseException as exc:
            exception_completer.completeExceptionally(java_future, exc.__class__.__name__, "".join(traceback.format_exception(exc)))
    task.add_done_callback(done)
    return java_future

def __micronaut_completion_stage_awaitable(java_loop, time_unit, executor_adapter=None, java_future=None):
    """Create a Python future that a Java ``CompletionStage`` can complete.

    Java calls this when Python code awaits a Java async value. The future is
    created on the running Micronaut loop if one exists, otherwise the helper
    falls back to the current Python event loop. If the Python future is
    cancelled, cancellation is propagated back to the Java future when Java
    supplied one.
    """

    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        if java_loop is not None:
            loop = __micronaut_install_asyncio_event_loop(java_loop, time_unit, executor_adapter)
            future = loop.create_future()
            if java_future is not None:
                future.add_done_callback(lambda completed: java_future.cancel(False) if completed.cancelled() else None)
            return future
        loop = asyncio.get_event_loop()
    future = loop.create_future()
    if java_future is not None:
        future.add_done_callback(lambda completed: java_future.cancel(False) if completed.cancelled() else None)
    return future

def __micronaut_complete_completion_stage_awaitable(future, value, throwable):
    """Complete a Python future from a Java ``CompletionStage`` callback."""

    if future.cancelled():
        return
    if throwable is None:
        future.set_result(value)
    else:
        future.set_exception(RuntimeError(str(throwable)))
