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
The classes below translate Python asyncio scheduling, timers and Java
``CompletionStage`` bridging into operations that can be driven by a
Micronaut-provided Java event-loop facade; stream and datagram transports are
the Java objects that facade creates.

Maintainer notes:
* Keep Java host calls small and explicit. Public Python behavior should look
  like normal asyncio, but the implementation must avoid blocking a Netty event
  loop.
* Prefer adding support behind capability checks on ``self._java_loop``;
  ``micronaut-context-python-netty`` provides the Netty-native factories and a
  loop without them raises ``NotImplementedError``.
* Do not expose raw Netty channels or handlers from this module. Java transport
  facades provide stable socket-like extras for Python callers.
* Keep unsupported APIs deterministic. Raising ``NotImplementedError`` is
  preferable to silently taking a blocking or semantically incomplete path.

Implementation map:
* ``_MicronautAsyncioHandle`` and ``_MicronautAsyncioTimerHandle`` preserve
  asyncio callback/timer cancellation state while Java owns execution timing.
* ``_MicronautNettyTransport`` and ``_MicronautNettyDatagramTransport`` are
  the ``asyncio.Transport`` objects protocols see: thin Python classes over the
  Java transports the Netty event loop creates for every address family, so
  keyword arguments (``set_write_buffer_limits(high=...)``, ``sendto(data,
  addr=...)``) and ``isinstance`` checks work. A Python socket cannot be
  adopted, so ``sock=`` arguments raise ``NotImplementedError``. The
  ``sock_*`` coroutines drive a caller-supplied non-blocking socket from the
  loop by retrying.
* ``_MicronautNettyServer`` adapts Java's Netty server facade to the Python
  ``asyncio.Server`` contract without exposing Netty implementation objects.
* ``_MicronautAsyncioEventLoop`` is the central event-loop adapter over the
  Java/Netty capabilities.
* The ``__micronaut_*`` functions at the bottom are the stable Java entry
  points used by ``PythonAsyncioRuntime``.
"""

import asyncio
import math
import contextvars
import errno
import inspect
import os
import select
import socket
import ssl as _micronaut_ssl
import threading
import traceback
import java

from asyncio import events
from asyncio import futures
from asyncio import tasks

class _PerThreadAttributes:
    """Per-thread attribute storage that lives and dies with this context.

    asyncio keeps its running loop, and the default policy its current loop, in ``threading.local``
    objects. GraalPy stores those in a Java ``ThreadLocal`` of the calling thread, so an entry made
    from a long-lived thread (a Netty event loop, a request thread, a test worker) keeps a closed
    context reachable through the thread. A dict keyed by thread ident is released with the context.
    """

    def __init__(self, **defaults):
        object.__setattr__(self, "_defaults", defaults)
        object.__setattr__(self, "_by_thread", {})

    def __getattr__(self, name):
        by_thread = object.__getattribute__(self, "_by_thread")
        defaults = object.__getattribute__(self, "_defaults")
        values = by_thread.get(threading.get_ident())
        if values is not None and name in values:
            return values[name]
        if name in defaults:
            return defaults[name]
        raise AttributeError(name)

    def __setattr__(self, name, value):
        by_thread = object.__getattribute__(self, "_by_thread")
        defaults = object.__getattribute__(self, "_defaults")
        ident = threading.get_ident()
        values = by_thread.get(ident)
        if value == defaults.get(name, _UNSET):
            # back to the default: the thread's entry is dropped, not kept
            if values is not None:
                values.pop(name, None)
                if not values:
                    by_thread.pop(ident, None)
            return
        if values is None:
            values = by_thread[ident] = {}
        values[name] = value


_UNSET = object()

# asyncio's running-loop holder and the default policy's loop storage, without threading.local
events._running_loop = _PerThreadAttributes(loop_pid=(None, None))
_policy_local = getattr(asyncio.get_event_loop_policy(), "_local", None)
if _policy_local is not None:
    asyncio.get_event_loop_policy()._local = _PerThreadAttributes(_loop=None, _set_called=False)


class MicronautJavaException(RuntimeError):
    """Python exception carrying a Java ``Throwable`` that failed an awaited Java value.

    GraalPy cannot attach a traceback to a foreign exception, which is what
    ``asyncio.Future.result`` does when a stored exception is re-raised. Java
    failures are therefore stored behind this Python exception. The bridge
    unwraps ``java_exception`` when the failure crosses back into Java, so the
    original Java exception type is preserved end to end. It is a ``RuntimeError``
    so Python code that handled the previous ``RuntimeError(str(throwable))``
    keeps working; the Java exception is available as ``java_exception``.
    """

    def __init__(self, java_exception):
        super().__init__(str(java_exception))
        self.java_exception = java_exception

_AsyncioRuntime = java.type("io.micronaut.context.python.PythonAsyncioRuntime")


def _report_loop_error(text):
    """Log an event-loop error through Micronaut's logger, falling back to stderr."""
    try:
        _AsyncioRuntime.reportLoopError(text)
    except BaseException:
        import sys
        print(text, file=sys.stderr)


class MicronautJavaOSError(OSError):
    """A Java I/O failure as the ``OSError`` asyncio code handles; ``java_exception`` is the Java throwable."""

    def __init__(self, java_exception):
        super().__init__(str(java_exception))
        self.java_exception = java_exception


class MicronautJavaConnectionRefused(ConnectionRefusedError):
    def __init__(self, java_exception):
        super().__init__(str(java_exception))
        self.java_exception = java_exception


class MicronautJavaTimeout(TimeoutError):
    def __init__(self, java_exception):
        super().__init__(str(java_exception))
        self.java_exception = java_exception


class MicronautJavaAddressError(socket.gaierror):
    def __init__(self, java_exception):
        super().__init__(socket.EAI_NONAME, str(java_exception))
        self.java_exception = java_exception


_TIMEOUT_FAILURES = (
    "io.netty.channel.ConnectTimeoutException",
    "io.netty.handler.ssl.SslHandshakeTimeoutException",
    "io.netty.resolver.dns.DnsNameResolverTimeoutException",
    "java.net.SocketTimeoutException",
    "java.util.concurrent.TimeoutException",
)


def _java_class_names(throwable):
    """The class names of a Java throwable and its superclasses, most specific first."""
    names = []
    try:
        java_class = throwable.getClass()
        while java_class is not None:
            names.append(java_class.getName())
            java_class = java_class.getSuperclass()
    except Exception:
        pass
    return names


def _java_failure_type(throwable):
    """The Python exception type for a Java throwable: networking failures become OSError subclasses.

    Timeouts are recognised before their broader parents (Netty's connect timeout is a
    ConnectException, its TLS handshake timeout an IOException).
    """
    names = _java_class_names(throwable)
    if any(name in _TIMEOUT_FAILURES for name in names):
        return MicronautJavaTimeout
    if "java.net.ConnectException" in names:
        return MicronautJavaConnectionRefused
    if "java.net.UnknownHostException" in names:
        return MicronautJavaAddressError
    if "java.io.IOException" in names:
        return MicronautJavaOSError
    return MicronautJavaException


def _is_java_failure(exception):
    return getattr(exception, "java_exception", None) is not None


def _micronaut_java_failure(throwable):
    failure = _java_failure_type(throwable)(throwable)
    try:
        failure.__cause__ = throwable
    except Exception:
        pass
    return failure

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
        # as asyncio: a callback runs in the context of the code that scheduled it
        self._context = contextvars.copy_context() if context is None else context
        self._cancelled = False

    def cancel(self):
        self._cancelled = True

    def cancelled(self):
        return self._cancelled

    def _run(self):
        if self._cancelled:
            return
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

class _MicronautNettyTransport(asyncio.Transport):
    """asyncio transport over the Java TCP/TLS/Unix transport created by the Netty event loop."""

    __slots__ = ("_java",)

    def __init__(self, java_transport):
        super().__init__()
        self._java = java_transport

    def get_extra_info(self, name, default=None):
        return self._java.get_extra_info(name, default)

    def is_closing(self):
        return self._java.is_closing()

    def close(self):
        self._java.close()

    def abort(self):
        self._java.abort()

    def set_protocol(self, protocol):
        self._java.set_protocol(protocol)

    def get_protocol(self):
        return self._java.get_protocol()

    def is_reading(self):
        return self._java.is_reading()

    def pause_reading(self):
        self._java.pause_reading()

    def resume_reading(self):
        self._java.resume_reading()

    def set_write_buffer_limits(self, high=None, low=None):
        self._java.set_write_buffer_limits(high, low)

    def get_write_buffer_size(self):
        return self._java.get_write_buffer_size()

    def get_write_buffer_limits(self):
        return tuple(self._java.get_write_buffer_limits())

    def write(self, data):
        self._java.write(data)

    def writelines(self, list_of_data):
        self._java.writelines(list(list_of_data))

    def write_eof(self):
        self._java.write_eof()

    def can_write_eof(self):
        return self._java.can_write_eof()


class _MicronautNettyDatagramTransport(asyncio.DatagramTransport):
    """asyncio datagram transport over the Java UDP transport created by the Netty event loop."""

    __slots__ = ("_java",)

    def __init__(self, java_transport):
        super().__init__()
        self._java = java_transport

    def get_extra_info(self, name, default=None):
        return self._java.get_extra_info(name, default)

    def is_closing(self):
        return self._java.is_closing()

    def close(self):
        self._java.close()

    def abort(self):
        self._java.abort()

    def set_protocol(self, protocol):
        self._java.set_protocol(protocol)

    def get_protocol(self):
        return self._java.get_protocol()

    def sendto(self, data, addr=None):
        if self.is_closing():
            return
        peer = self._java.get_extra_info("peername")
        if peer is None:
            if addr is None:
                raise ValueError("unconnected datagram transport requires an address")
        elif addr is not None and _normalized_address(addr) != _normalized_address(peer):
            raise ValueError(f"Invalid address: must be None or {_normalized_address(peer)}")
        self._java.sendto(data, addr)


def _normalized_address(address):
    """An address tuple as (host, port, flowinfo, scope_id): two IPv6 addresses differing only by scope differ."""
    host = str(address[0])
    port = int(address[1])
    flowinfo = int(address[2]) if len(address) > 2 and address[2] is not None else 0
    scope_id = int(address[3]) if len(address) > 3 and address[3] is not None else 0
    return (host, port, flowinfo, scope_id)


class _MicronautNettyServer:
    """Python facade over a Java Netty-backed asyncio server.

    The Java object owns the channel and graceful close future. This facade
    adapts that object to Python's ``asyncio.Server`` shape while preserving the
    invariant that no raw Netty channel leaks into application code.
    """

    def __init__(self, loop, java_servers):
        self._loop = loop
        self._java_servers = list(java_servers)
        self._closed = False
        self._serving_forever = None

    @property
    def sockets(self):
        if self._closed:
            return []
        return [server_socket for java_server in self._java_servers for server_socket in java_server.sockets()]

    def get_loop(self):
        return self._loop

    async def start_serving(self):
        # a coroutine, as on asyncio.Server: ``await server.start_serving()``
        for java_server in self._java_servers:
            java_server.startServing()

    async def serve_forever(self):
        if self._serving_forever is not None:
            raise RuntimeError(f"server {self!r} is already being awaited on serve_forever()")
        if self._closed:
            raise RuntimeError(f"server {self!r} is closed")
        # the future close() cancels: serve_forever() then raises CancelledError, as asyncio.Server does
        self._serving_forever = self._loop.create_future()
        try:
            await self.start_serving()
            await self._serving_forever
        except asyncio.CancelledError:
            # cancelled from outside or by close(): the server is closed either way
            self.close()
            await self.wait_closed()
            raise
        finally:
            self._serving_forever = None

    def close(self):
        self._closed = True
        for java_server in self._java_servers:
            java_server.close()
        serving_forever = self._serving_forever
        if serving_forever is not None and not serving_forever.done():
            serving_forever.cancel()

    async def wait_closed(self):
        for java_server in self._java_servers:
            await self._loop._completion_stage_to_future(java_server.waitClosed())

    def is_serving(self):
        return not self._closed and all(java_server.isServing() for java_server in self._java_servers)

    async def __aenter__(self):
        return self

    async def __aexit__(self, *exc):
        self.close()
        await self.wait_closed()

class _MicronautAsyncioEventLoop(asyncio.AbstractEventLoop):
    """Asyncio event loop driven by a Micronaut ``PythonEventLoop`` facade.

    This is not a general-purpose selector loop. It is a bridge that lets
    coroutine code use the subset of asyncio that Micronaut can safely drive
    from Java. Methods are grouped into scheduling, future/task creation,
    executor handoff, networking, socket helpers, and deterministic unsupported
    APIs. Networking operations take the Netty path through the factory methods
    of ``self._java_loop`` for every address family; a loop without them, or a
    caller-supplied Python socket, raises ``NotImplementedError``.
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
        raise RuntimeError("Micronaut-managed asyncio loops are driven by the Netty EventLoop and cannot be stopped")

    def is_running(self):
        return True

    def is_closed(self):
        return False

    def close(self):
        # asyncio refuses to close a running loop; this loop runs as long as its Netty event loop
        raise RuntimeError("Cannot close a running event loop")

    def time(self):
        return self._java_loop.time()

    def call_soon(self, callback, *args, context=None):
        self._check_closed()
        handle = _MicronautAsyncioHandle(callback, args, context)
        # executeCallback runs the Python callable inside an execution frame of this context
        self._java_loop.executeCallback(lambda: self._run_handle(handle))
        return handle

    def call_soon_threadsafe(self, callback, *args, context=None):
        return self.call_soon(callback, *args, context=context)

    def call_later(self, delay, callback, *args, context=None):
        return self.call_at(self.time() + delay, callback, *args, context=context)

    def call_at(self, when, callback, *args, context=None):
        self._check_closed()
        handle = _MicronautAsyncioTimerHandle(when, callback, args, context)
        delay = max(0.0, when - self.time())
        handle._scheduled_future = self._java_loop.scheduleCallback(lambda: self._run_handle(handle), int(delay * 1000000000), self._time_unit)
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
        """Report a callback failure the way asyncio does: log it, do not propagate it into Netty."""
        message = context.get("message") or "Unhandled exception in event loop"
        exception = context.get("exception")
        lines = [message]
        for key in sorted(context):
            if key in ("message", "exception"):
                continue
            lines.append(f"{key}: {context[key]!r}")
        if exception is not None:
            lines.append("".join(traceback.format_exception(type(exception), exception, exception.__traceback__)).rstrip())
        _report_loop_error("\n".join(lines))

    def call_exception_handler(self, context):
        if self._exception_handler is None:
            try:
                self.default_exception_handler(context)
            except BaseException as failure:
                _report_loop_error(f"Exception in default exception handler: {failure!r}")
            return
        try:
            self._exception_handler(self, context)
        except BaseException as failure:
            # a failing custom handler is reported through the default one, as asyncio does
            try:
                self.default_exception_handler({
                    "message": "Unhandled error in exception handler",
                    "exception": failure,
                    "context": context,
                })
            except BaseException as nested:
                _report_loop_error(f"Exception in default exception handler: {nested!r}")

    async def shutdown_asyncgens(self):
        # Async generators are finalised by the garbage collector: the loop keeps no registry of them.
        pass

    async def shutdown_default_executor(self, timeout=None):
        # The blocking executor belongs to Micronaut and outlives the loop; there is nothing to shut down.
        pass

    def _check_closed(self):
        if self._closed:
            raise RuntimeError("Event loop is closed")

    def _unsupported(self, name):
        raise NotImplementedError(f"asyncio event-loop API [{name}] is not supported by the Micronaut Netty event loop")

    @staticmethod
    def _host_of(address):
        """The host of an address tuple; an IPv6 scope id (the fourth element) is kept as host%scope."""
        host = address[0]
        if len(address) >= 4 and address[3]:
            return f"{host}%{address[3]}"
        return host

    @staticmethod
    def _address_family(family, python_name):
        """The address family name the Java loop filters resolved addresses by."""
        if family in (0, socket.AF_UNSPEC):
            return ""
        if family == socket.AF_INET:
            return "inet"
        if family == socket.AF_INET6:
            return "inet6"
        raise NotImplementedError(f"asyncio event-loop API [{python_name}] does not support address family {family!r} on the Micronaut Netty event loop")

    def _netty_factory(self, java_name, python_name):
        """The Java factory behind an asyncio API, or NotImplementedError for a loop without it."""
        factory = getattr(self._java_loop, java_name, None)
        if factory is None:
            self._unsupported(python_name)
        return factory

    @staticmethod
    def _has_ssl(ssl):
        # an empty mapping still asks for TLS with defaults; only None and False mean plaintext
        return ssl is not None and ssl is not False

    @classmethod
    def _check_ssl_timeouts(cls, ssl, ssl_handshake_timeout, ssl_shutdown_timeout, server_hostname=None):
        if not cls._has_ssl(ssl):
            if server_hostname is not None:
                raise ValueError("server_hostname is only meaningful with ssl")
            if ssl_handshake_timeout is not None:
                raise ValueError("ssl_handshake_timeout is only meaningful with ssl")
            if ssl_shutdown_timeout is not None:
                raise ValueError("ssl_shutdown_timeout is only meaningful with ssl")
            return
        for name, timeout in (("ssl_handshake_timeout", ssl_handshake_timeout), ("ssl_shutdown_timeout", ssl_shutdown_timeout)):
            if timeout is None:
                continue
            # as asyncio: a positive, finite number of seconds
            if isinstance(timeout, bool) or not isinstance(timeout, (int, float)) or not math.isfinite(timeout) or timeout <= 0:
                raise ValueError(f"{name} should be a positive number, got {timeout!r}")

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
            # runs on the loop, inside an execution frame of this context (see completeOnLoop)
            if future.cancelled():
                return
            if throwable is None:
                future.set_result(value)
            else:
                future.set_exception(_micronaut_java_failure(throwable))
        _AsyncioRuntime.completeOnLoop(stage, self._java_loop, complete)
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

    @staticmethod
    def _numeric_host(host, flags):
        # the one getaddrinfo flag with a visible effect: with AI_NUMERICHOST every host that would
        # be resolved must already be a literal address, as getaddrinfo would insist
        if host is None or host == "" or not (flags & socket.AI_NUMERICHOST):
            return
        import ipaddress
        try:
            ipaddress.ip_address(host)
        except ValueError:
            raise socket.gaierror(socket.EAI_NONAME, f"Name or service not known: {host!r} is not a numeric host") from None

    async def create_connection(self, protocol_factory, host=None, port=None, *, ssl=None, family=0, proto=0, flags=0, sock=None, local_addr=None, server_hostname=None, ssl_handshake_timeout=None, ssl_shutdown_timeout=None, happy_eyeballs_delay=None, interleave=None, all_errors=False):
        # Netty resolves the host itself; the family argument narrows the resolved addresses the
        # way asyncio's getaddrinfo lookup would. proto, flags and all_errors have no Netty
        # equivalent and are accepted; the happy-eyeballs options are refused rather than ignored.
        self._check_ssl_timeouts(ssl, ssl_handshake_timeout, ssl_shutdown_timeout, server_hostname)
        ssl = self._java_ssl(ssl)
        if sock is not None:
            self._unsupported("create_connection(sock=...): the Netty event loop cannot adopt a Python socket")
        if happy_eyeballs_delay is not None or interleave is not None:
            self._unsupported("create_connection(happy_eyeballs_delay=..., interleave=...): the Netty event loop tries the resolved addresses one after the other")
        if proto not in (0, socket.IPPROTO_TCP):
            self._unsupported(f"create_connection(proto={proto!r}): the Netty event loop opens TCP connections")
        if host is None or port is None:
            raise ValueError("host and port are required")
        self._numeric_host(host, flags)
        if local_addr is not None:
            self._numeric_host(local_addr[0], flags)
        family_name = self._address_family(family, "create_connection")
        netty_factory = self._netty_factory("createConnection", "create_connection")
        local_host = None
        local_port = -1
        if local_addr is not None:
            local_host = self._host_of(local_addr)
            local_port = local_addr[1]
        try:
            connection = await self._completion_stage_to_future(netty_factory(protocol_factory, host, int(port), local_host, local_port, ssl, server_hostname, ssl_handshake_timeout, ssl_shutdown_timeout, family_name))
        except Exception as failure:
            if not _is_java_failure(failure):
                raise
            if all_errors:
                # every address was tried; the Java failure carries the earlier attempts as suppressed exceptions
                attempts = [_micronaut_java_failure(suppressed) for suppressed in failure.java_exception.getSuppressed()]
                attempts.append(failure)
                raise ExceptionGroup("Multiple exceptions", attempts) from None
            raise
        return connection[0], connection[1]

    async def create_server(self, protocol_factory, host=None, port=None, *, family=socket.AF_UNSPEC, flags=socket.AI_PASSIVE, sock=None, backlog=100, ssl=None, reuse_address=None, reuse_port=None, keep_alive=None, ssl_handshake_timeout=None, ssl_shutdown_timeout=None, start_serving=True):
        self._check_ssl_timeouts(ssl, ssl_handshake_timeout, ssl_shutdown_timeout)
        ssl = self._java_ssl(ssl)
        if sock is not None:
            self._unsupported("create_server(sock=...): the Netty event loop cannot adopt a Python socket")
        if keep_alive is not None:
            self._unsupported("create_server(keep_alive=...)")
        family_name = self._address_family(family, "create_server")
        netty_factory = self._netty_factory("createServer", "create_server")
        # asyncio binds one listening socket per host; every host gets its own Netty server
        hosts = list(host) if isinstance(host, (list, tuple)) else [host]
        if not hosts:
            hosts = [None]
        # asyncio: an empty host means every interface, like None
        hosts = [None if one_host == "" else one_host for one_host in hosts]
        for one_host in hosts:
            self._numeric_host(one_host, flags)
        java_servers = []
        try:
            for one_host in hosts:
                java_servers.append(await self._completion_stage_to_future(netty_factory(protocol_factory, one_host, int(port or 0), int(backlog), reuse_address is not False, bool(reuse_port), bool(start_serving), ssl, ssl_handshake_timeout, ssl_shutdown_timeout, family_name)))
        except BaseException:
            for java_server in java_servers:
                java_server.close()
            raise
        return _MicronautNettyServer(self, java_servers)

    async def create_unix_connection(self, protocol_factory, path=None, *, ssl=None, sock=None, server_hostname=None, ssl_handshake_timeout=None, ssl_shutdown_timeout=None):
        self._check_ssl_timeouts(ssl, ssl_handshake_timeout, ssl_shutdown_timeout, server_hostname)
        ssl = self._java_ssl(ssl)
        if sock is not None:
            self._unsupported("create_unix_connection(sock=...): the Netty event loop cannot adopt a Python socket")
        if path is None:
            raise ValueError("path is required")
        netty_factory = self._netty_factory("createUnixConnection", "create_unix_connection")
        connection = await self._completion_stage_to_future(netty_factory(protocol_factory, str(path), ssl, server_hostname, ssl_handshake_timeout, ssl_shutdown_timeout))
        return connection[0], connection[1]

    async def create_unix_server(self, protocol_factory, path=None, *, sock=None, backlog=100, ssl=None, ssl_handshake_timeout=None, ssl_shutdown_timeout=None, start_serving=True):
        self._check_ssl_timeouts(ssl, ssl_handshake_timeout, ssl_shutdown_timeout)
        ssl = self._java_ssl(ssl)
        if sock is not None:
            self._unsupported("create_unix_server(sock=...): the Netty event loop cannot adopt a Python socket")
        if path is None:
            raise ValueError("path is required")
        netty_factory = self._netty_factory("createUnixServer", "create_unix_server")
        java_server = await self._completion_stage_to_future(netty_factory(protocol_factory, str(path), int(backlog), bool(start_serving), ssl, ssl_handshake_timeout, ssl_shutdown_timeout))
        return _MicronautNettyServer(self, [java_server])

    async def connect_accepted_socket(self, protocol_factory, sock, *, ssl=None, ssl_handshake_timeout=None, ssl_shutdown_timeout=None):
        self._check_ssl_timeouts(ssl, ssl_handshake_timeout, ssl_shutdown_timeout)
        ssl = self._java_ssl(ssl)
        netty_factory = self._netty_factory("connectAcceptedSocket", "connect_accepted_socket")
        connection = await self._completion_stage_to_future(netty_factory(protocol_factory, sock, ssl, ssl_handshake_timeout, ssl_shutdown_timeout))
        if connection is None:
            self._unsupported("connect_accepted_socket with a Python socket: only a channel accepted by the Netty event loop can be adopted")
        return connection[0], connection[1]

    async def create_datagram_endpoint(self, protocol_factory, local_addr=None, remote_addr=None, *, family=0, proto=0, flags=0, reuse_port=None, allow_broadcast=None, sock=None):
        if sock is not None:
            self._unsupported("create_datagram_endpoint(sock=...): the Netty event loop cannot adopt a Python socket")
        if proto not in (0, socket.IPPROTO_UDP):
            self._unsupported(f"create_datagram_endpoint(proto={proto})")
        if not (local_addr or remote_addr) and family in (0, socket.AF_UNSPEC):
            raise ValueError("unexpected address family")
        family_name = self._address_family(family, "create_datagram_endpoint")
        for address in (local_addr, remote_addr):
            if address is not None:
                self._numeric_host(address[0], flags)
        netty_factory = self._netty_factory("createDatagramEndpoint", "create_datagram_endpoint")
        local_host = None
        local_port = -1
        remote_host = None
        remote_port = -1
        if local_addr is not None:
            local_host = self._host_of(local_addr)
            local_port = local_addr[1]
        if remote_addr is not None:
            remote_host = self._host_of(remote_addr)
            remote_port = remote_addr[1]
        endpoint = await self._completion_stage_to_future(netty_factory(protocol_factory, local_host, local_port, remote_host, remote_port, bool(allow_broadcast), bool(reuse_port), family_name))
        return endpoint[0], endpoint[1]

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
            remaining = view[total_sent:]
            sent = await self._retry_socket_call(lambda: sock.send(remaining))
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

def __micronaut_netty_transport(java_transport):
    """Java entry point: the asyncio transport handed to ``connection_made`` for a stream channel."""
    return _MicronautNettyTransport(java_transport)


def __micronaut_netty_datagram_transport(java_transport):
    """Java entry point: the asyncio transport handed to ``connection_made`` for a datagram channel."""
    return _MicronautNettyDatagramTransport(java_transport)


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
    return loop


class _CurrentLoopForCall:
    """Make the loop current (running loop and policy loop) for one host-driven call, then restore.

    The calling thread is a request or event-loop thread that outlives the call; leaving the loop
    installed on it would keep per-thread state for every thread that ever entered this context.
    """

    def __init__(self, loop):
        self._loop = loop

    def __enter__(self):
        local = asyncio.get_event_loop_policy()._local
        self._saved = (events._get_running_loop(), local._loop, local._set_called)
        asyncio.set_event_loop(self._loop)
        events._set_running_loop(self._loop)
        return self._loop

    def __exit__(self, *exc):
        running, policy_loop, set_called = self._saved
        events._set_running_loop(running)
        local = asyncio.get_event_loop_policy()._local
        local._loop = policy_loop
        local._set_called = set_called
        return False

class _WakeableSelector:
    """The selector of a loop without sockets: ``select`` waits for a wake-up or the timeout."""

    def __init__(self):
        self._wake = threading.Event()

    def select(self, timeout=None):
        if timeout is None or timeout > 0:
            self._wake.wait(timeout)
        # a wake-up arriving after the wait and before the clear is not lost: the callback that
        # caused it is already in the loop's ready queue, which the loop drains before it selects again
        self._wake.clear()
        return []

    def wake(self):
        self._wake.set()

    def close(self):
        pass

    def get_map(self):
        return {}


class _MicronautFallbackLoop(asyncio.base_events.BaseEventLoop):
    """A loop for driving a coroutine on the calling thread in a context without host socket access.

    asyncio's selector loop opens a socket pair for its self-pipe, which the emulated POSIX layer of
    an application context refuses; this loop waits on an event instead, so scheduled callbacks,
    timers, ``call_soon_threadsafe`` and executors work while asyncio's own networking does not.
    """

    def __init__(self):
        super().__init__()
        self._selector = _WakeableSelector()

    def _process_events(self, event_list):
        pass

    def _write_to_self(self):
        self._selector.wake()

    def close(self):
        super().close()
        self._selector.close()


def _new_fallback_loop():
    try:
        return asyncio.new_event_loop()
    except OSError:
        # io.UnsupportedOperation ("socket was excluded"): no socket pair for the self-pipe
        return _MicronautFallbackLoop()


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
        with _CurrentLoopForCall(loop):
            task = loop.create_task(awaitable)
        java_future.setCancelCallback(lambda: loop.call_soon_threadsafe(task.cancel))
        def done(completed):
            try:
                if completed.cancelled():
                    java_future.cancel(False)
                    return
                exception = completed.exception()
                if exception is not None:
                    exception_completer.completeExceptionally(java_future, exception)
                    return
                java_future.complete(completed.result())
            except BaseException as exc:
                exception_completer.completeExceptionally(java_future, exc.__class__.__name__, "".join(traceback.format_exception(exc)))
        task.add_done_callback(done)
        return java_future
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        # No Micronaut event loop and no running Python loop: drive the awaitable to completion on the
        # current thread. The outcome is read from the future rather than from run_until_complete so
        # a Java exception raised inside the coroutine is not re-raised through Future.result, which
        # GraalPy cannot do for foreign exceptions.
        try:
            fallback_loop = _new_fallback_loop()
            try:
                completed = asyncio.ensure_future(awaitable, loop=fallback_loop)
                try:
                    fallback_loop.run_until_complete(completed)
                except BaseException:
                    pass
                if completed.cancelled():
                    java_future.cancel(False)
                else:
                    exception = completed.exception()
                    if exception is not None:
                        exception_completer.completeExceptionally(java_future, exception)
                    else:
                        java_future.complete(completed.result())
            finally:
                fallback_loop.close()
        except BaseException as exc:
            exception_completer.completeExceptionally(java_future, exc.__class__.__name__, "".join(traceback.format_exception(exc)))
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
                exception_completer.completeExceptionally(java_future, exception)
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
            with _CurrentLoopForCall(loop):
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
        future.set_exception(_micronaut_java_failure(throwable))
