# Python Coroutine Support For Micronaut/GraalPy

## Summary

- First fix the worktree base: `python-coroutines` is clean, has `0` commits ahead, and is `915` commits behind `python-support`, so fast-forward it with `git merge --ff-only python-support`. Do not touch the dirty sibling `python-support` worktree.
- Support Python `async def` as Micronaut async methods by generating Java `CompletionStage<T>` bridge methods, not by reusing Kotlin `isSuspend()`.
- Install a GraalPy `asyncio.AbstractEventLoop` implementation backed by the current Netty `EventLoop`, because asyncio's core contract covers coroutines/tasks/futures plus event-loop scheduling, timers, network IO, subprocesses, and callback APIs. Reference: [asyncio tasks and event loop documentation](https://docs.python.org/3.11/library/asyncio-task.html).

## Public Surface

Python users write normal coroutine-shaped controllers and clients:

```python
@Client("/backend")
class BackendClient(ABC):
    @Get("/message")
    @abstractmethod
    async def message(self) -> str:
        ...

@Controller("/demo")
class DemoController:
    client: Annotated[BackendClient, Inject]

    @Get
    async def index(self) -> str:
        return await self.client.message()
```

- For `async def f(...) -> T`, the generated Java method returns `CompletionStage<T>`. The Python annotation describes the awaited result, not `CompletionStage<T>`.
- Add an opt-out property: `micronaut.python.asyncio.enabled=false`. Otherwise async support is automatic when `context-python` plus the Netty integration module are present.
- Keep `ExecutableMethod.isSuspend()` Kotlin-only. Python async is represented through `CompletionStage` return types and internal Python metadata.

## Key Changes

- **Branch correction:** Fast-forward `python-coroutines` to `python-support`; if fast-forward fails, stop and inspect instead of resetting.
- **AST and processing:** Treat `ast.AsyncFunctionDef` like `FunctionDef` with an `isAsync` flag. Update `FunctionDef`, `PythonMethodElement`, script/class processing, and generated source descriptions so async methods preserve metadata.
- **Stub generation:** For async methods, generate `CompletionStage<T>` bridge signatures and convert the coroutine result into a stage. Reject async constructors, properties, and `@Bean` factory methods for v1 because Micronaut bean construction is synchronous.
- **Coroutine runtime:** Add `PythonAsyncioRuntime` in `context-python` to detect coroutine/awaitable `Value`s, schedule them on the active Python event loop, complete Java `CompletableFuture`s, map exceptions, cancellation, and final value conversion.
- **Netty asyncio integration:** Add a small `context-python-netty` integration module that depends on `context-python` and Netty. It provides a `PythonEventLoopProvider` backed by `io.netty.channel.EventLoop`.
- **Context model:** Async execution bypasses the blocking `PythonPool`. Maintain one GraalPy `Context` and class/script cache per Netty `EventLoop`; marshal all coroutine steps and callback execution onto that EventLoop. This avoids concurrent Context access, which GraalVM only allows when there is no simultaneous access or when all initialized languages support it. Reference: [GraalVM Polyglot Context Javadoc](https://www.graalvm.org/23.0/javadoc/sdk/org/graalvm/polyglot/Context.html).
- **Asyncio loop implementation:** Implement callback ordering, `call_soon`, `call_soon_threadsafe`, `call_later`, `call_at`, `time`, `create_future`, `create_task`, cancellation, exception handling, debug flags, executor offload, DNS, TCP/UDP transports, streams, sockets, subprocess APIs, and deterministic platform-specific failures for APIs Netty/JVM cannot support.
- **Netty transport implementation:** Keep low-level `loop.sock_*` APIs compatible with Python `socket.socket`, but implement high-level transport factories through Netty where the Netty integration is present. In particular, `create_connection`, `create_server`, `connect_accepted_socket`, and `create_datagram_endpoint` should use Netty `Channel`s and Netty's TCP/UDP/datagram APIs underneath instead of polling Python sockets from `context-python`. The core `context-python` loop may retain socket-based fallbacks for non-Netty usage, but `context-python-netty` should override/delegate transport creation to Netty-backed host implementations for readiness, backpressure, lifecycle, and datagram behavior.
- **Client await support:** Wrap Java `CompletionStage` results returned into Python as awaitables tied to the captured Netty-backed loop, so `await client.method()` resumes on the same EventLoop.
- **AOP/interceptors:** Extend `PythonProxyCreator` boxing/unboxing so Python coroutine results and Java `CompletionStage` results flow through existing `CompletionStageInterceptedMethod` handling.

## Test Plan

- Processor tests in `inject-python-test`: async AST detection, generated `CompletionStage<T>` signatures, result type unwrapping, invalid async factory/constructor/property failures, and compatibility with abstract `@Client` methods.
- Runtime tests in `context-python`: coroutine-to-`CompletionStage`, exception propagation, cancellation, nested `await`, `asyncio.sleep`, `gather`, callback ordering, timer cancellation, and awaitable wrapping of Java `CompletionStage`.
- Netty integration tests in the new module: `call_soon_threadsafe` from non-EventLoop threads, timer scheduling on Netty `ScheduledFuture`, per-EventLoop context isolation, context shutdown, and no use of `PythonPool.borrow()` on EventLoop threads.
- Netty transport tests in the new module: exercise `asyncio.sleep`, callback ordering, `create_connection`/`create_server`, `connect_accepted_socket`, `asyncio.open_connection`/`asyncio.start_server`, and `create_datagram_endpoint` on a real Netty `EventLoop`. Assert transport creation uses Netty channels rather than the socket-polling fallback when `context-python-netty` is active.
- End-to-end `test-suite-python`: async controller awaits async Python `@Client`; configure `micronaut.netty.event-loops.default.num-threads=1`, fire many concurrent requests using `await asyncio.sleep(100ms)`, and assert total time is concurrent rather than serialized.
- Non-blocking proof: record Netty EventLoop thread before/after `await`, add a heartbeat scheduled with `loop.call_later`, and fail the test if heartbeat jitter shows the EventLoop was blocked during client await coordination.
- Run focused checks first: `./gradlew :inject-python-test:test :context-python:test :context-python-netty:test :test-suite-python:test`, then broaden to affected HTTP/AOP tests if failures indicate shared behavior.

## Assumptions

- Existing synchronous Python `def` behavior and pooled sync execution remain unchanged.
- Async generators are out of scope for this pass; streaming remains through existing `Publisher` support.
- `asyncio.run()` inside a Micronaut-managed async request should raise the normal nested-loop style error instead of blocking the Netty EventLoop.
- `run_in_executor` uses Micronaut's blocking executor; if guest-created Python threads are needed later, enable `Context.Builder.allowCreateThread(true)` deliberately because it is disabled by default. Reference: [GraalVM Context.Builder Javadoc](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html).

## Follow-Up

- Investigate supporting TLS and Unix-domain socket asyncio APIs in `context-python-netty` by configuring Netty SSL handlers for `create_connection`, `create_server`, `connect_accepted_socket`, `start_tls`, and `sendfile`, and by selecting Netty domain socket channel types for `create_unix_connection` and `create_unix_server`.
