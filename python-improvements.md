# Micronaut Python (GraalPy) implementation: improvements list

Tracking file for the review of `context-python`, `context-python-netty`, `inject-python`,
`inject-python-test` and `test-suite-python`. Numbers were measured on a stock OpenJDK 25
(Truffle fallback runtime), so absolute values are pessimistic but ratios hold.

Status legend: DONE = implemented and covered by tests in this worktree, OPEN = recommended,
not implemented (needs a design decision or is out of scope for this pass).

## Correctness fixes (first pass)

| # | Status | Improvement | Where |
|---|--------|-------------|-------|
| C1 | DONE | `Value.getContext()` returns a view object that is `equals` but not `==` to the creator context; runtime state is now keyed by `Context.equals`, and `enter()` is done through the creator instance (view contexts cannot be entered). | `PythonContextRuntime` |
| C2 | DONE | Closing the pool no longer cancels in-flight pooled executions: `closePool()` defers the real close until the active-execution counter reaches zero. | `PythonPool.closePool` / `closeContexts` |
| C3 | DONE | Graceful shutdown no longer rejects new borrows; only `ApplicationContext.close()` makes waiting borrows fail with "Pool closed". | `PythonPool.shutdownGracefully` |
| C4 | DONE | Exceptions crossing a Python coroutine keep their Java type and message instead of being flattened to `RuntimeError(str(e))` or failing inside `Future.result()` with "Cannot set property on interop exception". | `micronaut_asyncio.py`, `PythonAsyncioRuntime`, `GraalPyExceptionHandler.toHostThrowable` |
| C5 | DONE | `convertList` / `convertMap` / `convertSet` no longer swallow conversion failures into empty collections. | `PythonConversion` (then `GraalPyRuntimeUtil`) |
| C6 | DONE | The annotation processor no longer executes arbitrary attribute expressions with `eval` at compile time; literal values are resolved with `ast.literal_eval`. | `micronaut_processor.py` |
| C7 | DONE | `PyronautJavaCompiler` restores system properties by diff instead of clearing the whole table. | `PyronautJavaCompiler` |
| C8 | DONE | Bean definition write failures are reported through the visitor context instead of `System.err`. | `PythonBeanDefinitionProcessor` |
| C9 | DONE | Script stub `INSTANCE` is assigned in the constructor (sourcegen cannot assign static fields lazily). | `PythonStubGenerator` |
| C10 | DONE | `resetContext()` only drops the primary context's state instead of every context's counters and shutdown gates. | `PythonContextRuntime.resetContext` |
| C16 | DONE | `newIntroduction` stubbed every abstract method and ran `update_abstractmethods` on each instantiation: it checked a `__micronaut_introduction__` marker that nothing ever set, and imported `update_abstractmethods` into the context's main bindings. `PythonProxyCreator` carried its own copy of the same stubbing plus two Python sources evaluated from Java strings. | One runtime-module helper (`__micronaut_prepare_introduction`) prepares a class once per context and sets the marker; both callers use it, and the proxy creator's scoped-proxy and raw-instance factories moved into `micronaut_runtime.py` behind the now `@Internal`-public `PythonContextRuntime.helper`. |
| C17 | DONE | A failed write on a Netty-backed stream transport called `connection_lost` directly, so the protocol could see it twice (once from the write failure, once when the channel closed), against asyncio's once-only contract. | The write failure is fired through the pipeline; the socket handler reports `connection_lost` once and closes the channel. |
| C18 | DONE | Warnings and info messages from Python type element visitors went to `System.out` even inside javac, so they never reached the compiler's messager, were not attributed to an element, and vanished in a Gradle worker. Only `fail` was delegated. | `PythonVisitorContext` delegates `warn` and `info` to the Java visitor context when there is one; the console remains for the bare parser. |
| P13 | DONE | `PythonAstParser` evaluated its driver snippets (`Source.create(PYTHON, getSource())`) once per source file, parsing the same snippet again for every one of the hundreds of files and transforms of a compilation. | The three driver snippets are static `Source`s marked `cached(true)`; GraalPy parses each once per context. |
| C15 | DONE | A connected asyncio datagram transport (`create_datagram_endpoint(remote_addr=...)`) wrote every datagram as a packet addressed to its remote. NIO tolerates that; the native transports fail the send with `EISCONN` (`sendToAddress(..) failed with error(-56): Socket is already connected`), so on epoll, kqueue and io_uring a connected UDP client never sent anything. Found by the per-transport datagram test on kqueue. | A connected channel writes plain buffers; an explicit address is only wrapped in a packet when it differs from the remote. |
| C14 | DONE | The Python asyncio loop chose Netty channel classes by the event loop's class name (`.epoll.`, `.kqueue.`). Netty 4.2 event loops are all `SingleThreadIoEventLoop`, whatever transport their `IoHandler` uses, so on Linux with epoll or io_uring (Micronaut's default order when the native transport is present) the loop registered NIO channels on a native loop and every Python connection, server and datagram endpoint failed; the same on macOS with kqueue. `reuse_port` and Unix socket addresses were affected the same way. | The transport is asked for through `IoEventLoop.isCompatible` with the transport's `IoHandle` class (class names as a fallback for Netty 4.1 loops), resolved once per loop; io_uring channel classes are supported. `NettyPythonTransportTest` runs the TCP, datagram and Unix-socket round trips and the channel registration on every transport available on the machine: NIO everywhere, kqueue on macOS, epoll on Linux CI. |
| C13 | DONE | An `async def` whose return annotation is a Python class (a dataclass, say) produced a bean definition that dispatched to `Note load()` while the generated stub declared `CompletionStage<Note> load()`: `resolveReturnType` returned the Python class element early and skipped the `CompletionStage` wrap. Every such route failed with `NoSuchMethodError` at request time. | The Python class element is wrapped like every other awaited type; `PythonClassElementSpec` asserts both return types and `PythonAsyncioSpec` round-trips a dataclass body through a pooled event-loop route. |
| C12 | DONE | `PythonConversion.convertList` sized a container first and then indexed into it, so a generator was exhausted by the size pass and converted to an empty list; dicts converted to a list of nothing. | One pass over the interop iterator (`convertElements`); dicts now convert to their keys, like `list(d)`. |
| C11 | DONE | Documented that per-event-loop contexts are not counted against `micronaut.python.pool.size`. | `pythonThreading.adoc` |

## Performance improvements (second pass)

| # | Status | Improvement | Measured before | Expected after | Where |
|---|--------|-------------|-----------------|----------------|-------|
| P0 | note | `convertValue(str, String)` went from 5.8 µs to 0.9 µs as a consequence of P1; no code change of its own. | 5.8 µs | 0.9 µs (measured) | `GraalPyRuntimeUtil.convertValue` |
| P1 | DONE | `isNone` is a plain `value == null \|\| value.isNull()` check. Python `None` is an interop null, so the meta-object lookup (and `toString` fallback) that ran on every non-null value was pure overhead. Called on every bridge return, every `fromPolyglotValue`, every list element and map entry. | 5.8 µs per call | 0.02 µs (measured) | `PythonConversion.isNone` |
| P2 | DONE | `invokePythonMethod` calls the member directly when `canExecute()` is true; the Python `__micronaut_invoke_method` helper is only used to bind raw descriptors. | 8.4 µs per bridge call | 3.5 µs (measured; the remainder is `withExecutionFrame` bookkeeping and `getMember`) | `PythonInvocation.invokePythonMethod` |
| P3 | DONE | Introspected (dataclass) stubs only re-sync fields to the Python object when a field changed since the last sync. When every property is immutable-typed (primitives, boxed, `String`, enums, `BigDecimal`, `UUID`, `java.time`), each gets a private snapshot field compared by reference; a class with any mutable-typed property (collection, array, nested wrapper) keeps the always-sync behaviour and gets no snapshot fields. Also stops overwriting attribute changes made on the Python side when Java did not touch the field. | one `putMember` helper call per field per `asPolyglotValue()` (about 5 µs each), twice per bridge call | zero guest writes when unchanged | `PythonStubGenerator` (`graalpyInternalSynced_*` fields) |
| P4 | DONE | The generated `Value` constructor reads each member once into a local instead of twice (`isNone` check plus conversion). | 2 `getMember` per field | 1 |  `PythonStubGenerator.polyglotValuePropertyAssignments` |
| P5 | DONE | `PythonVisitorContext` caches its `PythonAnnotationMetadataBuilder` and `PythonElementAnnotationMetadataFactory` instead of creating new ones (and discarding their caches) on every call, matching `JavaVisitorContext`. | new builder per call, 8 call sites | one per processing run | `PythonVisitorContext` |
| P6 | DONE | `getSize` answers through the array protocol (`hasArrayElements` / `getArraySize`) before probing `__len__` with a guest call; `convertMap` already prefers `hasHashEntries`. | 37 µs for 5 ints, 37 µs for a 2-entry map | 2.6 µs and 5.1 µs (measured) | `PythonConversion` (`convertElements`) |
| P7 | DONE (measured, small) | Compile time is 73% GraalPy guest execution and 33% javac; the first context takes 2.7 s to start and the Gradle plugin forked a fresh JVM per `compileTestPython`. `PythonCompile` now runs the compiler through the Worker API in a process-isolated worker. A JVM-wide shared GraalPy `Engine` for every `PythonAstParser` was tried and reverted: an engine pins every context created on it until it is closed and the optimizing runtime keeps compiled code per engine, so the compile-time suite (hundreds of parsers in one JVM) ran out of heap on GraalVM CE and its test executor died, which Gradle reported as a hang. Gradle's public Worker API keeps a worker alive for one build only (`keepAliveMode=SESSION`), so a build with a single Python compile task gains nothing: `:test-suite-python:compileTestPython --rerun` takes 19.3 s under the worker and 19.5 s under `javaexec` on OpenJDK 25, and 22.5 s on GraalVM CE where a short-lived process pays for Truffle compilation it never amortises. Builds with several Python compile tasks share the daemon. The remaining cost is the processor itself running interpreted; a persistent compiler process is not reachable through Gradle's public API. | 19.3 s vs 19.5 s per compile | | `buildSrc` `PythonCompile`, `PythonCompileWorkAction`, `PythonAstParser` |
| P8 | DONE (narrowed; import clauses fixed in X8) | Incremental compilation dependency analysis is regex based and fell back to full reprocessing of every Python source on any `getattr(` anywhere in the project. The import graph itself is already recorded: javac's reference scanner attributes generated stubs to their Python origin, and the scan resolves `import`/`from` statements. What was wrong was the `getattr` rule: it now forces reprocessing only when the target is a name bound by an import (the attribute may then be another source's declaration), while `getattr` on any other object is ordinary attribute access. `__import__`, `import_module`, `globals()`, `locals()`, star imports and `@Mixin` still force it. Emitting the graph from the processor's AST would not change these decisions, so it is not done. | | | `IncrementalCompilation`, `PyronautCompilerIncrementalTest` (two new cases) |
| P9 | measured, no action | The `Python -> Java` host-access predicate cost does not scale with the number of `TargetTypeMapping` beans (0 vs 300 mappings: 3.3 vs 2.8 µs). No action needed; recorded so nobody optimises it blindly. | | | `GraalPyHostAccessFactory` |
| P10 | DONE | Python classes are resolved once per context and cached in the context state. Before, every dataclass rebuilt in a pooled context re-imported its module and asked `inspect.isclass`; for a class living in a submodule that included two failing imports (19 µs each on GraalVM CE, exception creation in Python) plus a `pkgutil` scan, on every request. | 19% of request-thread CPU in `findClass` (JMH body endpoint, 4 threads, GraalVM CE, JFR 1 ms); 8809 executor samples | 2.6%; 7072 executor samples at the same throughput (20% less CPU per request) | `PythonContextRuntime.findClass` |
| P11 | DONE (small gain) | Rebuilding a dataclass in a pooled context writes all fields with one guest call (`putMembers`) instead of one `putMember` call per field. Measured effect is small: the cost is the guest-side `setattr` work, not the interop call. Kept because the generated code is shorter and it removes four helper invocations per rebuild. | 11.0% of request-thread CPU, 7072 executor samples | 10.1%, 6914 samples (about 2% less executor CPU) | `PythonCoercion.putMembers`, generated `reconstructPolyglotValue` |
| P12 | DONE | An engine option configured through `graalpy.engine.options` now wins over the built-in `engine.CompilerThreads=1` default; previously the default was applied last and silently overrode the user's value. | | | `GraalPyEngineFactory.optionalOptionsToApply` |

## GraalVM findings (optimizing Truffle runtime)

Measured with the standalone probe and the `PythonRequestBodyPoolBenchmark` JMH benchmark. GraalPy 25.2.4 only gets a JIT on a GraalVM JDK from the matching 25.2 release line; every JDK on this machine (Oracle GraalVM 25.0.3, GraalVM CE 25.0.2, Oracle GraalVM 25.0.4, GraalVM 26 EA, OpenJDK 25) runs it interpreted, and the `org.graalvm.compiler:compiler` 25.2.4 artifact on the upgrade module path rejects all of them (JVMCI API mismatch). GraalVM CE 25.2.4 (`graal-25.2.4` release, JDK 25.0.4 + jvmci-25.2) reports `runtime=GraalVM CE`. CI pins GraalVM `25.2` so it does run compiled.

| # | Status | Finding | Interpreted | GraalVM CE 25.2.4 |
|---|--------|---------|-------------|-------------------|
| G1 | DONE | Docs never said which JDK is needed; added a "Runtime Requirements" page and an INFO line at startup naming the active runtime (`Interpreted` means fallback). | | |
| G2 | measured | Bridge call `invokePythonMethod` (standalone) | 0.83 µs | 0.37 µs |
| G3 | measured | Python loop code (10k iterations) | 465 to 930 µs | 15 µs |
| G4 | measured | Python reading three Java getters | 2.4 µs | 0.42 µs |
| G5 | measured | `newUninitializedInstance` (class in `__main__`) | 7 to 12 µs | 1.6 µs |
| G6 | measured | Pooled context creation, steady state | 0.35 to 0.55 s | 0.6 to 1.0 s (JIT compile threads compete) |
| G7 | measured | HTTP throughput, body endpoint, 1 / 4 client threads | 5.9k / 19.3k req/s | 6.7k / 20.7k req/s |
| G8 | measured | HTTP throughput, control endpoint, 1 / 4 client threads | 8.5k / 25.7k req/s | 8.4k / 25.3k req/s |
| G9 | measured | In-process compile of the petclinic fixture (steady state) | 0.8 s | 0.95 s; one-shot compilation does not benefit from the JIT, 75% of samples stay in guest code |
| G10 | DONE | The HTTP benchmark is latency bound (blocking client, thread hop to the IO executor), so runtime changes show up as CPU per request, not throughput. | `PythonRequestBodyPoolBenchmark` reports the process CPU time of each iteration as the `cpuMillis` auxiliary counter next to the throughput; CPU per request is `cpuMillis/s` divided by `ops/s`. | |
| G11 | DONE | The bootstrap (primary) context used its own `Engine`, separate from the pooled engine bean, so compiled code was not shared between them and two compiler queues existed. | The engine bean returns the reusable context's engine when one is installed; the reusable context stays registered, so the destruction listener never closes it. `GraalPyContextFactoryTest` asserts the pool shares the bootstrap engine and that closing the application leaves it usable. | |
| G12 | DONE | `example.TestMain` in `test-suite-python` timed out once (read timeout) under the parallel full-suite run on GraalVM CE and passed alone in 0.37 s; JIT warm-up under load. | The suite runs with `micronaut.http.client.read-timeout=30s`; module tests cannot carry a `@Property` of their own. | |
| G13 | measured | Test suites on GraalVM CE 25.2.4: all pass, including the engine-option test that fails on the fallback runtime. | | |

## Design items (need a decision)

| # | Status | Item | Recommendation |
|---|--------|------|----------------|
| D1 | DONE | Per-event-loop contexts were unbounded: one 37 MB context per Netty event loop regardless of `micronaut.python.pool.size`. Counting them against the pool size would deadlock once event loops hold every context, because an event-loop context is never returned. | `micronaut.python.pool.max-event-loop-contexts` (default 0, no cap): the first loops to run Python are admitted, the others are reported as having no Python event loop and run through the shared pool, blocking while a coroutine runs; a warning names each refused loop. Documented in the threading guide; `PythonAsyncioRuntimeTest` covers admission. Since Q1 the pooled context stays leased until the coroutine completes. |
| D2 | DONE (with a documented limit) | `PythonContextRuntime` kept all context bookkeeping in static maps, which is how `resetContext` could wipe a live pool's counters (fixed narrowly in C10). | The application-owned state (primary context, class loader, pool, offload executor) is now one `PythonApplicationRuntime` object created with the primary context and exposed as a bean; `PythonPool` and the asyncio configurer bind to it by injection. The context registry (`PythonContextRegistry`) stays JVM-wide on purpose: it is keyed by `Context`, and shutdown gates must see every execution whichever application started it. Generated code still resolves the runtime installed last, so one JVM serves generated bridge code from one application at a time; `uninstall` is a compare-and-set so a runtime replaced while its shutdown was pending cannot remove its successor. |
| D3 | DONE | `test-suite-python` disables the pool globally; `PythonAsyncioSpec` already enables it, but no route there took or returned a dataclass, so `PooledValueCoercible`, `reconstructPolyglotValue` and the per-context value cache had no end-to-end coverage. | `AsyncDemoController.echo_note` takes a `Note` body and returns a new `Note` from the event-loop context; the spec asserts the round trip. Writing it found C13. |
| D4 | DONE | `GraalPyContextFactory` allowed lookup of every host class (`allowHostClassLookup(_ -> true)`). | `graalpy.context.host-class-lookup` lists the package prefixes Python code may look up; empty (the default) keeps every class visible, and the JDK, Jakarta and `io.micronaut` packages are always visible because generated Python code depends on them. Documented in the runtime guide. |
| D5 | DONE | No pool metrics (borrow wait time, exhaustion, event-loop context count). Core has no Micrometer dependency, so a binder cannot live here. | `PythonContextExecutor.statistics()` returns a `PythonPoolStatistics` snapshot (target, pooled, idle and event-loop context counts, borrows, waits, total and longest wait); the `pythonpool` management endpoint serves it when `micronaut-management` is present. A Micrometer `MeterBinder` over the snapshot is a one-liner for applications; documented in the threading guide. |

## External review (Codex, gpt-5.6-sol, read-only over the branch diff)

Run with `codex exec --sandbox read-only` against the branch. Thirteen findings; outcome per finding:

| # | Severity | Finding | Outcome |
|---|----------|---------|---------|
| X1 | Blocker | `PythonCompileWorkAction` and `PythonCompileParameters` were referenced by `PythonCompile` but never committed: `.gitignore`'s `build/` rule matches the package directory `io/micronaut/build/`, and the stale `buildSrc/build/classes` hid it locally. | DONE: both files force-added. A clean checkout builds. |
| X2 | High | `PythonPool.createBorrowedPooledContext` checked `closed`, then registered the new context outside the monitor, so `closePool` could snapshot and clear the pool in between: the context was never closed, and `release` would then keep it because it was still in `pooledContexts`. | DONE: registration happens under the pool monitor and a context created after close is closed at once. |
| X3 | High | Netty protocol callbacks (`connection_made`, `data_received`, `connection_lost`, `datagram_received`, `error_received`) and protocol factories ran guest code straight from the channel thread: no current Python event loop for nested bridge calls, no execution frame, so shutdown could consider the context idle while a callback was entering it. | DONE: every host-to-Python call in `NettyPythonEventLoop` goes through `guest(channel, value, op)`, which binds the channel's event loop and opens an execution frame on the guest's context; `PythonContextRuntime.withExecutionFrame` is the `@Internal` entry point. `NettyPythonEventLoopSemanticsTest` asserts both from inside a callback. The second review found the same gap for `call_soon`/`call_later` callbacks: see Y2. |
| X4 | High | Deleting `GraalPyRuntimeUtil` breaks code generated by `5.2.x` snapshots. | Not applicable: the module is unreleased, there is no published generator. Recorded in Q22. |
| X5 | High | `runWithExecutionFrame` entered the context before its `try`, so a closed or cancelled context left the frame and the execution counters incremented and graceful shutdown could wait forever. | DONE: entry happens inside the `try` and the frame unwinds whether or not entry succeeded. `PythonContextRuntimeStateTest.executionFrameUnwindsWhenTheContextCannotBeEntered`. |
| X6 | High | `PythonCompile` submitted one worker action per source directory, all writing the same destination, and Gradle may run work items concurrently. | DONE (completed in Y1): one work item carries every source root; the first fix still ran one compiler per root, which overwrote the aggregate outputs, so the roots now go to one compiler invocation. |
| X7 | Medium | `shutdownGracefully` completes once the pool is idle and then keeps serving borrows, which reads against `GracefulShutdownCapable`'s "fully shut down". | Deliberate (C3): the pool is shared with request handling that may still run during a graceful shutdown; refusing borrows there fails requests the server is still draining. `ApplicationContext.close()` is the point after which borrows fail. Documented in `PythonPool.shutdownGracefully`. |
| X8 | Medium | The narrowed `getattr` rule (P8) only recognised the first clause of `import a, b as p`, so `getattr(p, ...)` did not force reprocessing. | DONE: every comma-separated clause and alias is scanned. `PyronautCompilerIncrementalTest.getattrOnAModuleBoundByAnImportClauseReprocessesEveryPythonSource`; backslash-continued imports followed in Y15, and the import graph itself (which had kept only the first clause) in W11. |
| X9 | Medium | `start_serving=False` disabled auto-read in the `NettyServer` constructor, after bind, so the channel could already have read. | DONE: `ChannelOption.AUTO_READ` is set on both server bootstraps before bind. `NettyPythonEventLoopSemanticsTest.serverDoesNotAcceptBeforeStartServing`. |
| X10 | Medium | `create_connection`, `create_server` and `create_datagram_endpoint` ignored `family` and the happy-eyeballs options, and `create_server` dropped every host after the first. | DONE: `family` is passed to the Java loop as `inet`/`inet6`, which resolves with `resolveAll` and picks the first address of that family (a literal of the wrong family fails); `create_server` binds one Netty server per host and `_MicronautNettyServer` fronts the list; `happy_eyeballs_delay`/`interleave` raise `NotImplementedError`. Multi-address fallback, `proto`, `flags` and `all_errors` followed in Y7. Docs updated. |
| X11 | Medium | `get_write_buffer_size()` returned the room left before the high-water mark, `get_write_buffer_limits()` hard-coded low to 0, and the setter changed the marks one at a time. | DONE: size is the outbound buffer's pending bytes, limits are the channel's `WriteBufferWaterMark`, and `set_write_buffer_limits` applies asyncio's defaults (64 KiB high, low a quarter of high) as one water mark. While fixing it: transports were Java host objects, so `set_write_buffer_limits(high=...)`, `sendto(data, addr=...)` and `get_extra_info(name, default=...)` with keywords raised `TypeError`; the loop now hands protocols `asyncio.Transport`/`asyncio.DatagramTransport` subclasses (`_MicronautNettyTransport`, `_MicronautNettyDatagramTransport`) over the Java transports, plus `is_reading` and `get_protocol` on both; `set_protocol` is exposed for the contract but raises, as the unsupported list says. The water marks only became flow control in Y4. |
| X12 | Medium | Datagram `connection_lost` was only called from `transport.close()`, not when the provider closed the channel at shutdown. | DONE: `NettyDatagramHandler.channelInactive` reports it once, like the stream handler. `NettyPythonEventLoopSemanticsTest.datagramTransportCloseReportsConnectionLostOnce`; the provider-shutdown path, which that test did not reach, is covered by `datagramTransportValidatesAddressesAndReportsProviderShutdown` (Y11). |
| X13 | Medium | `hasAnnotationAliasTarget` only looked at `AliasFor(annotation=...)`, so `AliasFor(annotationName=...)` was treated as a same-annotation member alias. | DONE: both target forms are checked. `AliasForQualifierSpec` "alias for names its target annotation by name". |

Design remarks from the same review, with the position taken: application state is routed to the last-installed
runtime (recorded in D2; two application contexts in one JVM are not a supported configuration of this module).
The event-loop cap counts admitted loops for their lifetime because Netty event loops live as long as the server;
the default stays unbounded so existing configurations keep one context per loop (D1). Host-class lookup stays
opt-in (D4). The `annotate` override carries stereotype members only when the declared annotation is absent and the
stereotype is present (Q20). The package split (Q27) is a source break for nobody: the module is unreleased.

### Second review (same tool, over the fixes above and the whole branch)

Fifteen findings; outcome per finding:

| # | Severity | Finding | Outcome |
|---|----------|---------|---------|
| Y1 | Blocker | The single work item still ran one `PyronautCompiler` per source root; every run rewrote `PyronautMain`, `__main__.py` and the VFS file list, so the last root won. | DONE: the roots are joined into one comma-separated `pythonSrc`, which the compiler already tokenises, and one invocation compiles them all. |
| Y2 | High | `call_soon`/`call_later` callbacks ran through `PythonEventLoop.execute(Runnable)` with nothing but the loop binding: a timer scheduled by a coroutine that already returned ran outside any execution frame, after shutdown may have closed the context. | DONE: `PythonEventLoop.executeCallback`/`scheduleCallback` take the Python callable as a `Value`; the Netty loop runs it inside an execution frame of its context and skips it once the context is unregistered. `scheduledCallbacksOutlivingTheirCoroutineRunInsideAnExecutionFrame`. The check and the entry became one step in Z1. |
| Y3 | High | `_MicronautNettyServer` did not follow `asyncio.Server`: `start_serving()` was synchronous, cancelling `serve_forever()` left the listener open, no `get_loop`, no async context manager, `sockets` populated after close. | DONE: all of it. `serverFollowsTheAsyncioServerContract`. `close()` cancelling `serve_forever()` and `wait_closed()` waiting for accepted connections followed in W5. |
| Y4 | High | The water marks were reported but never drove `pause_writing`/`resume_writing`. | DONE: `channelWritabilityChanged` pauses and resumes once per transition. `writabilityDrivesPauseAndResumeWriting` pushes 4 MiB through a paused reader. |
| Y5 | High | `getAnyClass`/`getAnyScript` peeked at the idle queue and handed out a value of a context another thread could borrow next. | DONE: a caller that owns no context gets the primary context's value, which the pool never lends; pooled callers re-coerce through `PooledValueCoercible`. |
| Y6 | High | `NettyPythonEventLoopSupport.closeAll` closed a snapshot; a channel tracked afterwards was never closed, and the JVM-wide support meant one application's shutdown closed another's channels. | DONE (completed in Z3): the provider owns its support; the "current provider" indirection of the first fix is gone. |
| Y7 | High | A hostname connection tried only the first resolved address; `proto`, `flags` and `all_errors` were ignored. | DONE: the resolved addresses of the family are tried in order, the last failure carries the others as suppressed exceptions and `all_errors=True` raises an `ExceptionGroup`; `proto` other than TCP raises `NotImplementedError`; `AI_NUMERICHOST` rejects a non-literal host with `gaierror`; the other flags are documented as having no effect. `connectionArgumentsAreHonouredOrRejected`. |
| Y8 | Medium | A context that failed while evaluating its main modules stayed registered, and the reusable bootstrap leaked its engine. | DONE: `GraalPyContextFactory.buildContext` unregisters and closes the context on failure; `bootstrapReusableContext` closes the engine it created. |
| Y9 | Medium | A datagram endpoint whose remote did not resolve stayed bound. | DONE: `closeOnCancellation` became `closeOnFailure`: a channel whose future fails for any reason is closed. |
| Y10 | Medium | `connect_accepted_socket` kept the adopted channel open when the protocol factory or TLS setup threw. | DONE: same helper. |
| Y11 | Medium | `sendto` accepted no address on an unconnected transport and a foreign address on a connected one, wrote after close, and `is_closing()` ignored closes by the peer or the provider. | DONE: the Python wrapper raises `ValueError` as asyncio does, writes after close are dropped, and `is_closing()` follows the channel on both transports. `datagramTransportValidatesAddressesAndReportsProviderShutdown`. A send whose name resolution finished after the close is covered by Z6. |
| Y12 | Medium | `set[T]`, `typing.Set[T]` and `FrozenSet[T]` resolved to `Object`: the raw-name switch lacked the set spellings, so the `Set` generic branch was unreachable. | DONE: mapped to `java.util.Set`. `PythonClassElementSpec` "set return types resolve to java sets with their element type". |
| Y13 | Medium | Only the bare `Annotated` name was recognised: `typing.Annotated[str, NotBlank]` and `from typing import Annotated as A` lost the type and the constraint. | DONE: `_is_annotated_name` recognises the bare name, the `typing.` attribute and an import alias; every call site uses it. `ValidationAnnotationSpec` "qualified and aliased Annotated spellings keep their constraints". |
| Y14 | Medium | A Java annotation without `@Target` was treated as not applicable to annotation types, against Java's rule, so a Python decorator function carrying only `@Singleton` was not an annotation declaration. | DONE: absent `@Target` permits annotation types. `MetaAnnotationTargetSpec`. |
| Y15 | Medium | A backslash-continued import (`import os, \` newline `alpha as module`) was not scanned for bound names. | DONE: explicit continuations are folded before scanning. `getattrOnAModuleBoundOnAContinuedImportLineReprocessesEveryPythonSource`. |

Also from that review: `PythonAsyncioRuntime.updateState` is a read-modify-write and is now synchronized; the `.s`
fallback and the processor's `except BaseException: pass` the Q14 row claimed gone were removed (the asyncio module keeps one on purpose, around a fallback `run_until_complete` whose outcome is read from the future); `GraalPyEngineFactoryTest`
now configures `engine.WarnInterpreterOnly`, an option both runtimes know, so the suite is green on the fallback
runtime too. Kept as is: `shutdownGracefully` completing while borrows continue (X7).

### Third review (same tool, over the fixes above and the whole branch)

Twelve of the fifteen Y fixes were confirmed; Y2, Y6 and Y11 were judged incomplete, and fourteen further items were raised:

| # | Severity | Finding | Outcome |
|---|----------|---------|---------|
| Z1 (completed in W2) | High | Closing was not a barrier: `onNoActiveExecutions` observed zero and released the registry lock before the close listener ran, the Netty callback checked "tracked" separately from entering, and `withExecutionFrame` recreated the state of an unregistered context. | DONE: a context state carries a `closing` flag set under the registry lock by `closeWhenIdle` (used by context destruction and pool close); entering is `tryEnterExecution` under the same lock and refuses a closing context for outermost entries while nested entries of an earlier execution complete; `tryWithExecutionFrame` neither revives an unregistered context nor separates the check from the count. `PythonContextRuntimeStateTest.aCloseSelectedWhileIdleRefusesNewExecutionsButNotNestedOnes`. |
| Z2 (completed in W1) | High | The per-context Java monitor was held across guest calls in the asyncio runtime, the pool and the primary-context helper, although the runtime's own comment says GraalPy holds its GIL across host calls: a thread owning the monitor while waiting for the GIL and a Python thread owning the GIL while re-entering a bridge that wants the monitor deadlock. | DONE: no guest call runs under the monitor any more; it protects only Java-side state (the async member map). The GIL serialises guest execution. |
| Z3 (completed in W3) | High | `currentSupport` was a JVM-wide "last provider constructed" value used by every static binding, the server pipeline customizer included, so a second provider stole the first one's channel tracking. | DONE: the customizer is injected with the provider and binds through it; a provider tracks and closes the channels of the loops it bound; the static `bind` methods remain for code without a provider (tests) and track nothing an application shutdown closes. A late channel is still closed asynchronously without anything awaiting it. |
| Z4 | Medium | Protocol factories ran before the connection existed: a DNS or connect failure called a factory asyncio never calls, and one protocol was shared across the sequential attempts. | DONE: the stream handler creates the protocol when the connection activates, the datagram endpoint after bind and connect (reads start then). `connectionArgumentsAreHonouredOrRejected` counts one factory call across a failed and a successful attempt. |
| Z5 (completed in W6) | Medium | `local_addr` was reduced to one address before the remote attempts; `create_server` with a hostname bound only the first resolved address. | DONE: local candidates are paired with each remote by family; the server binds every resolved address and fronts them all. `serverBindsEveryAddressOfAName`. |
| Z6 (completed in W7) | Medium | A datagram send waiting on DNS could write, and report `error_received`, after the transport closed. | DONE: the resolution listener and the write recheck closing; a write failure after close is not reported. |
| Z7 (completed in W8) | Medium | `server_hostname` and the TLS timeouts were accepted without `ssl`, and a datagram endpoint with no address and no family opened a wildcard socket. | DONE: `ValueError` up front, as in `BaseEventLoop`. `invalidArgumentCombinationsAreRejectedUpFront`. |
| Z8 (completed in W6) | Medium | IPv6 addresses were two-element tuples and a scope id was discarded. | DONE: `(host, port, flowinfo, scope_id)` for IPv6, and a scope id on a literal is kept. |
| Z9 | Medium | The default exception handler re-raised into Netty, a failing custom handler was not contained, and the two shutdown coroutines silently did nothing. | DONE: the default handler logs through Micronaut's logger, a failing custom handler is reported through the default one; the shutdown coroutines stay no-ops and the unsupported list says why. `callbackFailuresGoToTheExceptionHandler`. |
| Z10 | Medium | The reusable bootstrap creates an engine nobody closes. | By design: a reusable context lives for the JVM (`resetContext` only reloads its modules), so its engine does too. Documented here. |
| Z11 | Low | `import typing as t; t.Annotated[...]` and `typing_extensions.Annotated` were not recognised. | DONE: the attribute base is resolved through the import table. `ValidationAnnotationSpec` covers the module alias. |
| Z12 | Low | The introduction "prepared" marker was inherited, so a subclass adding abstract methods was never prepared. | DONE: the marker is read from the class's own `__dict__`. `PythonRuntimeModuleTest.anAbstractSubclassOfAPreparedIntroductionIsPreparedOnItsOwn`. |
| Z13 | Low | Source roots travel as a comma-separated string. | Accepted: a comma in a source directory name is not supported by the compiler's existing option format. |
| Z14 | Low | `forgetContext` subtracted a context's executions from the aggregate and their later exits subtracted them again. | DONE: an exit decrements the aggregate only when its context state counted it; `unregisterContext` subtracts like `forgetContext`. |

Also recorded from that review: Q22's wording now says which of the proposed classes were created; Q17's line
count is updated.

### Fourth review (same tool, over the fixes above and the whole branch)

Z4, Z9, Z11, Z12 and Z14 were confirmed; the rest were rated partial, and fourteen further items were raised:

| # | Severity | Finding | Outcome |
|---|----------|---------|---------|
| W1 | High | `PythonPool` ran guest code inside `ConcurrentHashMap.computeIfAbsent`/`computeIfPresent`: class loading, script loading with injections and `eval` under a map bin lock, which a Python thread re-entering the runtime for the same key would hit as a recursive update or as a bin wait while holding the GIL. | DONE: the caches load outside the map and publish with `putIfAbsent`; an injection recorded while a script loads is applied by the loader after publishing. |
| W2 (completed in V2) | High | Netty protocol callbacks used the strict `withExecutionFrame`, which creates the state of an unregistered context; the handlers evaluated the `bytes` helper outside any frame; `asyncInstance` loaded the event-loop class and copied members before the invocation frame. | DONE: `withTrackedExecutionFrame` refuses a closing or closed context without creating state and every guest call of the Netty loop uses it; the factory, the helpers and `connection_made` run in one frame; `asyncInstance` runs in a frame of the event-loop context. |
| W3 (completed in V1) | High | `closeAll` awaited only its snapshot, so a channel tracked after shutdown began was closed without anything awaiting it, and a resolver could be created after the resolvers were closed. | DONE: tracking, resolver creation and shutdown share one lifecycle lock; the shutdown stage completes when the last tracked channel, late ones included, has closed; a resolver cannot be created after shutdown. |
| W4 | Medium | `Map.copyOf` rejected a remembered async member set to `null`, so a nullable property copied into an event-loop context threw. | DONE: null-tolerant copy. |
| W5 | Medium | `server.close()` from outside let `serve_forever()` return normally, and `wait_closed()` ignored accepted connections. | DONE: `close()` cancels the future `serve_forever()` awaits so it raises `CancelledError`; the Java server tracks accepted connections and `wait_closed()` waits for them (asyncio 3.12). `closingTheServerCancelsServeForeverAndWaitClosedWaitsForClients`. |
| W6 (completed in V6) | Medium | Only the first same-family local address was tried per remote; a datagram endpoint used the first local and remote result only; IPv6 scope ids given at creation were dropped. | DONE: every (remote, local) pair of matching families is attempted; datagram local and remote candidates are paired by family and tried in turn; a scope id travels as `host%scope` and an IPv6 literal resolves locally. |
| W7 | Medium | A datagram DNS callback queued before provider shutdown could write after it. | DONE: `is_closing()` on both transports also reports the provider's shutdown. |
| W8 | Medium | An empty TLS mapping counted as no TLS; `create_unix_connection` accepted `server_hostname` without `ssl`; `connect_accepted_socket` skipped the timeout checks. | DONE: one validator for every TLS-capable API; only `None` and `False` mean plaintext. |
| W9 | Medium | A `connection_lost` that threw skipped the channel close in `exceptionCaught`. | DONE: the close is in a `finally`. |
| W10 | Medium | `PyronautJavaCompiler` restores every system property changed during a compilation, which can undo a change an unrelated thread made meanwhile. | Accepted: the Gradle task runs the compiler in its own worker process; in-process embedding (tests) restores the diff because annotation processors are not allowed to leak properties, and there is no way to tell which thread changed one. |
| W11 (completed in V4) | Medium | The incremental import graph recorded only the first clause of `import a, b`, so a change to `b` did not reprocess the importer; an import statement did not end at `;`. | DONE: every clause feeds the graph, statements end at a semicolon. `everyClauseOfADirectImportIsADependency`. |
| W12 (completed in V5) | Medium | Annotation literals were narrowed silently: `256` became a `byte` of `0`, `"abc"` a `char` of `'a'`. | DONE: integral members require an integral, in-range literal and `char` members a one-character string, with an error naming the value; arrays the same. `AnnotationScalarsTest`. |
| W13 (completed in V8) | Low | Every Java failure surfaced as `RuntimeError`, so `except ConnectionRefusedError` and `except OSError` in asyncio code did not match. | DONE: connection refusal, an unresolvable name, a timeout and other I/O failures raise `ConnectionRefusedError`, `socket.gaierror`, `TimeoutError` and `OSError` subclasses that keep the Java throwable as `java_exception`. |
| W14 | Low | The loop said it was always running, yet `close()` succeeded and `stop()` did nothing. | DONE: both raise `RuntimeError`, as asyncio does for a running loop. |

Also from that review: `NettyPythonEventLoop` equality includes the provider's support, so two providers on one Netty
loop do not share a Python loop; Q22's and Q17's wording, the Y3 and X8 rows and the networking guide were brought in
line with the code.

### Fifth review (same tool, over the fixes above and the whole branch)

W1, W4, W5, W7, W8, W9 and W14 were confirmed; the rest were rated partial, and nine further items were raised:

| # | Severity | Finding | Outcome |
|---|----------|---------|---------|
| V1 | High | The shutdown stage could complete before a channel whose connect or bind was in flight was tracked, so that channel was closed without anything awaiting it; a factory begun after shutdown could complete with an already-closed channel. | DONE: every factory registers its operation with the support before starting and completes it with its future; shutdown refuses new operations and completes only when no operation is pending and no channel is tracked. |
| V2 | High | `asyncInstance` and the event-loop branches of `withPooled`, `withPooledScript`, `withPooledValue` and `PythonPool.withContext` ran on the event-loop context without a frame, or with the state-creating one. | DONE: all of them run their load and callback in a tracked frame of the event-loop context. |
| V3 | High | The asyncio module registered a Python callable directly with a Java `CompletionStage`, so it ran on the completing thread outside any frame; `completeAwaitable` and the `run_in_executor` worker ran guest code untracked, so a detached completion could enter a context after it went idle or closed. | DONE: `PythonAsyncioRuntime.completeOnLoop` completes Python callbacks on the loop inside a tracked frame and skips them once the context is closing; awaitable completions and executor callbacks are framed the same way. |
| V4 (completed in U2) | Medium | `import alpha; import beta` scanned only the first statement of the line. | DONE: `;`-separated statements are split before scanning. `anImportAfterASemicolonIsADependency` uses a module attribute, not a type, so only the import graph can relate the sources. The first version left lines containing a quote alone; U2 splits with a scanner that tracks strings, brackets and comments. |
| V5 | Medium | A huge double saturated to the type's maximum before the range check; numeric arrays accepted `"1"` and boolean arrays turned any string into `false`; annotation declaration defaults narrowed unchecked. | DONE: exact `BigDecimal` range checks, numeric and boolean array elements must be numbers and booleans, and declaration defaults go through the same rules. |
| V6 | Medium | An IPv6 host was reported with its `%scope` and the scope again as the fourth element, so `_host_of` doubled it. | DONE: the host is the unscoped address; the scope is the fourth element only. |
| V7 | Medium | `create_datagram_endpoint(factory, family=AF_INET)` without addresses found no address pair and failed; the NIO datagram channel was dual-stack and reported `::` for an IPv4 wildcard. | DONE: an explicit family binds that family's wildcard, and the datagram channel is created with the requested socket family. |
| V8 | Low | Netty's connect timeout (a `ConnectException`) surfaced as `ConnectionRefusedError` and its TLS handshake timeout as `OSError`. | DONE: timeout classes are recognised by name before their parents. |
| V9 | Low | `is_reading()` stayed `True` after the peer or the provider closed the channel. | DONE: it follows `is_closing()`. |

Positions on the two risks it raised: the caches may initialise a value twice under concurrent first access and keep
one, which is what a cache without a lock across guest code costs and is harmless for classes and modules; the
in-process compiler's property restoration stays as recorded under W10.

### Coverage pass over every review finding

Every finding of the five reviews that had a code change now has a test of the changed behaviour, except the ones
listed as not covered; where a test can only exercise part of a finding, the table says so. Added in this pass:

| Finding | Test |
|---------|------|
| V1 (shutdown waits for in-flight operations, refuses new ones) | `NettyPythonEventLoopSupportTest.shutdownWaitsForOperationsInFlightAndForTheirLateChannels` |
| V2 (event-loop executions in a tracked frame) | `PythonAsyncioRuntimeTest.eventLoopPooledExecutionsRunInsideATrackedFrame` (`withPooledScript` and `withPooledValue`; `asyncInstance`, `withPooled` and `PythonPool.withContext` share the code path but are not asserted) |
| V3 (stage completions and executor workers framed, detached completion) | `NettyPythonEventLoopSemanticsTest.executorWorkersAndStageCompletionsRunInsideAFrame` (the worker half fails without the fix; the completion half observes the callback after it was queued to the loop) |
| V8, W13 (Netty timeouts and other failures map to asyncio's exceptions) | `NettyPythonEventLoopSemanticsTest.javaFailuresMapToTheExceptionsAsyncioCodeExpects` |
| W9 (a throwing `connection_lost` still closes the channel) | `NettyPythonEventLoopSemanticsTest.aProtocolWhoseConnectionLostThrowsStillReleasesItsChannel` |
| Y5 (context-less lookups use the primary context) | `PythonAsyncioRuntimeTest.aCallerWithoutAContextGetsThePrimaryContextsScript` |
| Y8 (a failed bootstrap unregisters and closes its context) | `PythonContextRuntimeStateTest.aContextWhoseMainModuleFailsIsUnregisteredAndClosed`, with the test VFS module `failing_main.py` |
| Z14 (a forgotten context's executions are subtracted once) | `PythonContextRuntimeStateTest.forgettingAContextLeavesTheOtherContextsExecutionsCounted` |
| W1 (no guest code inside map remapping functions) | `PythonAsyncioRuntimeTest.aScriptThatReentersThePoolForItselfWhileLoadingLoads`, with the test VFS module `reentrant.py` whose body re-enters the pool for itself |
| W4 (a nullable async member survives the copy) | `PythonAsyncioRuntimeTest.aNullableAsyncMemberIsCopiedIntoTheEventLoopContext` |
| W6, Z5 (local names paired by family for TCP and UDP) | `NettyPythonEventLoopSemanticsTest.localAddressNamesArePairedWithTheRemotesFamily` |
| W8 (an empty TLS mapping is TLS; accepted sockets validate timeouts) | `invalidArgumentCombinationsAreRejectedUpFront` (extended) |
| W14 (`stop()` refused) | `closingTheServerCancelsServeForeverAndWaitClosedWaitsForClients` (extended) |
| Y12 (`FrozenSet[str]` element type) | `PythonClassElementSpec` (extended) |
| Z8, V6 (a four-element IPv6 tuple is accepted by `sendto`; scope zero only, a non-zero scope needs a link-local interface) | `NettyPythonAsyncioRuntimeTest.nettyBackedRuntimeRunsDatagramsOverIpv6` (extended); this found a fourth bug: a Python tuple reaches Java as a list proxy, which `toSocketAddress` now accepts |

Not covered, and why: X2 (pool creation racing shutdown) needs the pool's monitor held between construction and
registration, which no fixture reaches; X6/Y1 (the Gradle worker with several source roots) needs a TestKit build;
Z2 (monitor versus GIL) is a deadlock whose reproduction would hang the suite; Z6/W7 (name resolution finishing
after a close) needs a resolver that can be paused; a non-zero IPv6 scope id needs a link-local interface; Y13's
`typing_extensions` is not installed in the test runtime.

Found while adding the tests: the runtime suite ran out of memory because closed contexts stayed reachable, about
40 MB each. Flight Recorder's old-object sampling with paths to GC roots (attach-based tools are blocked in the
sandbox) gave the roots:

| # | Root | Share of samples | Fix |
|---|------|------------------|-----|
| M1 | A Java `ThreadLocal` of the calling thread. asyncio keeps its running loop, and the default event-loop policy its current loop, in `threading.local` objects; GraalPy stores those in a Java `ThreadLocal` of the thread, and the entry's value (a Python dict, whose class alone reaches the whole context) keeps a closed context alive for as long as the thread lives. Every thread that ran a coroutine, Netty event loops and request threads included, pinned every context it had used. | 80% | `micronaut_asyncio` replaces both holders with `_PerThreadAttributes`, a dict keyed by thread ident that lives and dies with the context. This is the one that matters in production: contexts closed on long-lived threads (an application restarted in the same JVM, a test suite, a redeploy) were never released. |
| M2 | The registry's `CONTEXT_STATES`, for contexts closed without being unregistered: every context the runtime did not create itself (tests, embedders). A state keeps the helper values and with them the heap. | 12% | `forgetClosedContexts()` drops the states of closed contexts, recognising both the plain and the cancelled close. It runs by itself whenever a context is seen for the first time, so an embedder's closed contexts are released without an API call, and the JUnit extension of the runtime and Netty test modules runs it after each test as well. The probe is a context operation and runs outside the registry lock (U5). |
| M3 | The asyncio admission sets, which kept every event loop ever admitted together with the callbacks queued on it. | small | Changing the providers starts a new admission. |

Per-thread loop state is keyed by thread ident but exists only for the duration of a host-driven call: the entry
points make the loop current and restore the thread's previous state, so a reused ident cannot see a departed
thread's loop (U3). The runtime test task keeps an explicit 2 GB heap. After the fixes the heaviest test class peaks at about 210 MB
where it reached 1.2 GB before.
`PythonAsyncioRuntimeTest.aClosedContextIsReleasedByTheThreadThatRanItsCoroutines` pins M1: it runs a coroutine on
the test thread, closes the context, and asserts through a weak reference that a host object stored in the
context's globals becomes unreachable; it fails against the previous asyncio module and passes with the fix.

### Sixth review (same tool, over the fixes above, the leak fixes and the new tests)

V1, V7 and V8 confirmed with genuine tests, V2, V3, V5, V6, V9 and M3 confirmed with partial tests, V4, M1 and M2
incomplete, and five further items:

| # | Severity | Finding | Outcome |
|---|----------|---------|---------|
| U1 (completed in T1) | High | `injectScript` walked every cached context and wrote the injected member into pooled contexts another thread may have borrowed and into event-loop contexts that belong to their loop's thread, outside any frame; the primary-context path was unframed too. | DONE: an injection is recorded with a version; each context applies the injections of a script it is behind on when its owner asks for the script (a borrowed pooled context, an event-loop context on its loop, the primary context inside a frame). `injectionsAreAppliedByEachContextsOwnerNotBroadcastToBorrowedContexts`. |
| U2 (completed in T4) | Medium | The `;` split skipped lines containing a quote, so `label = "x"; import beta` did not record `beta`. | DONE: a scanner splits at semicolons outside strings, brackets and comments; the semicolon test now has a quoted semicolon on the line. The first version scanned one physical line at a time, so a string spanning lines confused it (T4). |
| U3 | Medium | The per-thread loop state was keyed by thread ident, which a later thread can reuse, and the install left the loop current on the calling thread. | DONE: the loop is current only for the duration of a host-driven call and the thread's previous state is restored, so no entry outlives the call. `aHostDrivenCallLeavesNoLoopStateOnItsThread`. |
| U4 | Medium | Connected-datagram validation compared host and port only, so two IPv6 addresses differing by scope passed. | DONE: the full `(host, port, flowinfo, scope_id)` tuple is compared. `connectedDatagramValidationDistinguishesIpv6Scopes` with synthetic peer tuples. |
| U5 | Medium | The closed-context sweep was reachable only from the test extensions and probed contexts under the registry lock. | DONE: it runs by itself when a context is seen for the first time, and the probe runs outside the lock with the state re-checked before removal. `statesOfContextsClosedWithoutUnregisteringAreDropped`. |

Design remarks kept as recorded earlier: one application runtime per JVM (D2), the unbounded event-loop context
default (D1), duplicate initialisation on a concurrent cache miss (V-round risks), the comma-joined source roots
(Z13).

### Seventh review (same tool, over U1 to U5 and the whole branch)

U3 and U4 confirmed, U5 functionally confirmed, U1 and U2 incomplete, and seven items:

| # | Severity | Finding | Outcome |
|---|----------|---------|---------|
| T1 (completed in R1) | High | `findPooledScript` and `findPooledClass` reached the event-loop context without a frame, so the import, the instantiation or a pending injection ran outside the lifecycle accounting; `getAnyClass` loaded on the shared primary context unframed. | DONE: both run in a tracked frame of the event-loop context, `getAnyClass` in a frame of the primary; the injection test now applies a pending injection through the raw lookup. |
| T2 (completed in R3, R4) | Medium | `create_server(host=None)` bound one wildcard of the platform's default family. | DONE: without a host and family the IPv6 and IPv4 wildcards are bound with channels of their family; on a dual-stack host the IPv4 bind of an already-served port is skipped. `aServerWithoutAHostListensOnEveryWildcardFamily`. Since S2 and Q3 the hostless server is one dual-stack IPv6 listener (IPv4 wildcard only without IPv6). |
| T3 | Medium | Scheduled callbacks with no explicit context ran in the loop's ambient `contextvars` context, not the scheduler's. | DONE: a handle copies the current context when none is given and always runs through it. `scheduledCallbacksRunInTheContextOfTheCodeThatScheduledThem`. |
| T4 | Medium | The statement splitter reset its string state at every line, so a string spanning lines hid or invented imports. | DONE: one scan over the whole source with persistent string, comment and bracket state; string contents are blanked. The semicolon test has a multi-line string containing an import-looking line. |
| T5 | Medium | `connect_accepted_socket` adopted a channel accepted on another event loop and re-piped it from the wrong loop. | DONE: refused with an error naming the cause. `aChannelAcceptedOnAnotherEventLoopCannotBeAdopted`. |
| T6 | Medium | Zero, negative or NaN TLS timeouts reached Netty. | DONE: `ValueError` for anything but a positive finite number, as asyncio. |
| T7 (completed in R6) | Low | A finite literal such as `1e100` became `Float.POSITIVE_INFINITY` in a `float` member. | DONE: rejected for scalars, arrays and declaration defaults. |

### Eighth review (same tool, over T1 to T7 and the whole branch)

T3 and T5 confirmed, T4 and T6 confirmed with tests to tighten, T1, T2 and T7 incomplete, and six items:

| # | Severity | Finding | Outcome |
|---|----------|---------|---------|
| R1 | High | With the pool disabled or context reuse on, `findPooledClass`/`findPooledScript` fell back to the unframed primary lookups, and the explicit-context overloads resolved without a frame. | DONE: the primary fallback runs in a frame of the primary context and the explicit-context overloads in a frame of the given context. |
| R2 | High | `from pkg import beta` recorded only `pkg`, so a change to `pkg/beta.py` did not reprocess an importer that names no type of it; `from . import beta` likewise. | DONE: each imported name is also a submodule candidate, kept when such a module exists. `aSubmoduleImportedFromItsPackageIsADependency`. |
| R3 (completed in S2) | Medium | A wildcard bind failure of one family was tolerated whatever its cause, so a real error left the server on one family. | DONE: only an unavailable family, or an IPv4 port already held by the dual-stack IPv6 listener, is tolerated; anything else closes what was bound and fails the call. |
| R4 | Medium | `create_server(host="")` reached Netty as a hostname. | DONE: an empty host means every interface, like `None`; the wildcard test covers both and requires the IPv6 listener whenever the host has IPv6. |
| R5 | Medium | TLS timeouts left unset fell to Netty's defaults rather than asyncio's 60 s and 30 s, and a positive value below a millisecond became Netty's no-timeout zero. | DONE: asyncio's defaults, and rounding up to at least a millisecond. `tlsTimeoutsFollowAsyncioDefaultsAndNeverRoundToZero`. |
| R6 | Low | Float array declaration defaults bypassed the range check. | DONE: array elements follow the component type's rules like scalar defaults. |

Also from that review: cleanup is failure-isolated (every no-active-executions listener runs and every pooled
context is closed before the first failure, an `Error` included since S4, is rethrown with its own type); the T4 test changes the module named only inside the
string and checks nothing is rebuilt; the TLS validation test covers negative, infinite, boolean and text values;
the X11 row no longer claims `set_protocol` is supported.

Found while timing the Netty tests of this round: a Python `bytes` built from a Java `byte[]` is read element by
element through interop, 7.8 s for 4 MiB on the interpreted runtime, while `bytes(ByteBuffer)` is one bulk copy
(7 ms). Every `data_received` and `datagram_received` paid the slow path; both now hand the protocol a
`ByteBuffer` (P14). The other direction was already a bulk copy.

### Ninth review (same tool, over R1 to R6 and the whole branch)

R2 and R4 confirmed with tests, R1, R5 and R6 correct but untested on the fixed paths, R3 incomplete, and four
items:

| # | Severity | Finding | Outcome |
|---|----------|---------|---------|
| S1 | High | `newInstance`, `newUninitializedInstance`, `newIntroduction`, `enumValue`, `newFrozenDataclassInstance` and the public `findClass` ran on the primary or a supplied context without a frame, so a constructor invoked outside a bridge call could race the close of the context. | DONE: every one of them runs inside a frame of its context. `generatedEntryPointsRefuseAPrimaryContextSelectedForClosing` calls each with the primary context selected for closing and expects the refusal; it also covers R1's pool-disabled fallback and explicit-context overload. Q2 completed it: the populated `newUninitializedInstance`, the public `findScript` and both runtime proxy creations run in one frame as well. |
| S2 | Medium | The IPv4 wildcard bind failure "already in use" after an IPv6 success was taken for the dual-stack collision, so an unrelated IPv4 listener on the port produced an IPv6-only server; the classification also relied on exception text. | DONE: no inference from text. The JDK and Netty's native transports create IPv6 sockets with `IPV6_V6ONLY` cleared, so a bound IPv6 wildcard is the whole server and the IPv4 wildcard is not attempted; only a host without IPv6 (an unsupported address type or a non-bind socket failure) falls back to IPv4, and a bind failure fails the call. The wildcard test expects one dual-stack listener reachable on both loopbacks. |
| S3 | Medium | A numeric `char[]` element was accepted through `toString()`. | DONE: array elements need a one-character string like scalars. |
| S4 | Low | The cleanup loops caught `RuntimeException` only, so an `Error` from one close skipped the rest. | DONE: every action runs; the first failure is rethrown with its own type. |

Tests added for the eighth round's fixes: `anSslHandlerGetsAsyncioTimeoutsWhenNoneAreGiven` builds the handler
(R5), `anArrayDeclarationDefaultOutOfRangeIsRejected` compiles a declaration whose `int[]` default overflows (R6; a Python `float` is a Java `double`, so the float range is unreachable from a declaration), and the TCP callback test
asserts the protocol receives `bytes` (P14).

### Tenth review (same tool, over S1 to S4, the new tests and the whole branch)

S3, S4, R5 confirmed; S1 incomplete, S2 questioned, and five items:

| # | Severity | Finding | Outcome |
|---|----------|---------|---------|
| Q1 | High | An async pooled bridge call borrowed a context, called the coroutine function, released the context and only then drove the coroutine, so with no admitted event loop (a refused loop, a plain thread) another caller could borrow the same context while the coroutine ran on it. | DONE: `invokePooledAsync`/`invokePooledScriptAsync` (emitted by the pooled stub generator for async methods) convert the coroutine to a stage while the context is leased and return it to the pool when the stage completes. `aPooledContextStaysLeasedUntilTheCoroutineItRunsCompletes` blocks a coroutine inside the only pooled context and shows a second bridge call waits for it. |
| Q2 | High | S1 left the populated `newUninitializedInstance` (frame ended before the member writes), the public `findScript` and the runtime proxy creations outside a frame. | DONE: each is one frame from lookup to last write; the closing-context test covers the first two, the proxy creations use the same facade. |
| Q3 | Medium | S2 stopped after a successful IPv6 wildcard bind and classified every non-bind `SocketException` (and an exception message) as "family unavailable", hiding unrelated socket failures behind an IPv4 fallback. | DONE: the family is chosen before binding by opening an IPv6 server socket (`UnsupportedOperationException` means none), one listener is bound and every bind failure fails the call; no exception text is read. The dual-stack claim rests on the JDK and Netty's native transports clearing `IPV6_V6ONLY` on the sockets they create. `aHostlessServerOnAPortInUseFails` occupies the wildcard port with a JDK channel and expects `OSError`. |
| Q4 | Medium | `AI_NUMERICHOST` was honoured for `create_connection`'s host only; a `local_addr`, a server host or a datagram address that needed resolving was resolved. | DONE: one check applied to every address that would be resolved; `theNumericHostFlagAppliesToEveryAddress` covers the four and a literal. |
| Q5 | Low | The bootstrap failure handlers caught `RuntimeException` from the cleanup close only, so an `Error` from it replaced the bootstrap failure. | DONE: `Error` is suppressed onto the original failure too. |
| Q6 (found while testing Q1) | High | A coroutine driven without an event loop (a plain thread, a refused loop) fell back to `asyncio.new_event_loop()`, whose selector loop opens a socket pair for its self-pipe; application contexts have no host socket access, so the emulated POSIX layer raised `io.UnsupportedOperation: socket was excluded` and the fallback never worked outside tests with `allowAllAccess`. | DONE: when the selector loop cannot be created the coroutine runs on `_MicronautFallbackLoop`, a `BaseEventLoop` whose selector waits on a `threading.Event` (timers, `call_soon_threadsafe` and executors work, asyncio's own networking does not). `aPooledContextStaysLeasedUntilTheCoroutineItRunsCompletes` runs on it inside an application context. |

Tests added for earlier rounds: `anIdleListenerThatThrowsAnErrorDoesNotStopTheOthers` (S4), a numeric `char[]` and an
out-of-range `float[]` annotation usage rejected at the usage level (S3, R6), and the datagram protocol asserts it
receives `bytes` (P14; the Java argument type itself is not observable from Python).

## Regression tests added

| Test | Covers |
|------|--------|
| `context-python-netty` `NettyPythonTransportTest` | C14: channel classes, registration and echo round trips per transport |
| `context-python-netty` `NettyPythonEventLoopSemanticsTest` | X3, X9, X10, X11, X12, Y2, Y3, Y4, Y7, Y11: callbacks and timers inside a bound execution frame, `start_serving=False`, the `asyncio.Server` contract, multi-host servers, `family` filtering, address fallback and argument rejection, write-buffer limits and flow control, datagram address validation and `connection_lost` on transport close and provider shutdown |
| `inject-python-test` `PythonClassElementSpec` (set types), `ValidationAnnotationSpec` (Annotated spellings), `MetaAnnotationTargetSpec` | Y12, Y13, Y14 |
| `inject-python` `PyronautCompilerIncrementalTest.getattrOnAModuleBoundOnAContinuedImportLineReprocessesEveryPythonSource` | Y15 |
| `context-python` `PythonContextRuntimeStateTest.aCloseSelectedWhileIdleRefusesNewExecutionsButNotNestedOnes` | Z1: closing is a barrier for outermost entries and callbacks, nested entries complete, an unregistered context is not revived |
| `context-python-netty` `NettyPythonEventLoopSemanticsTest` (`serverBindsEveryAddressOfAName`, `invalidArgumentCombinationsAreRejectedUpFront`, `callbackFailuresGoToTheExceptionHandler`, factory count) | Z4, Z5, Z7, Z8, Z9 |
| `context-python` `PythonRuntimeModuleTest.anAbstractSubclassOfAPreparedIntroductionIsPreparedOnItsOwn` | Z12 |
| `context-python-netty` `NettyPythonEventLoopSemanticsTest.closingTheServerCancelsServeForeverAndWaitClosedWaitsForClients` | W5, W13, W14: external close cancels `serve_forever`, `wait_closed` waits for a client, `ConnectionRefusedError`, `loop.close()` refused |
| `inject-python` `PyronautCompilerIncrementalTest.everyClauseOfADirectImportIsADependency`, `anImportAfterASemicolonIsADependency`, `AnnotationScalarsTest.literalsOutsideTheMemberTypeAreRejectedLikeJavaSource` | W11, W12, V4, V5 |
| `context-python-netty` `NettyPythonEventLoopSemanticsTest` (family-only datagram, `is_reading` after close) | V7, V9 |
| `context-python` `PythonContextRuntimeStateTest.executionFrameUnwindsWhenTheContextCannotBeEntered` | X5: counters unwind when entering a closed context fails |
| `inject-python` `PyronautCompilerIncrementalTest.getattrOnAModuleBoundByAnImportClauseReprocessesEveryPythonSource` | X8: `import a, b as alias` binds `alias` |
| `inject-python-test` `AliasForQualifierSpec` (`annotationName`) | X13: `AliasFor(annotationName=...)` targets the named annotation |
| `context-python-netty` `NettyPythonAsyncioRuntimeTest` (IPv6 connection, IPv6 datagrams, Python socket rejection) | Q17: every address family on Netty, no socket fallback |
| `context-python` `PythonAsyncioRuntimeTest.aLoopWithoutNettyFactoriesRejectsConnectionApis` | Q17: deterministic failure without the Netty module |
| `test-suite-python` `PythonPoolEndpointSpec` | D5: the pool statistics endpoint serves the snapshot |
| `context-python` `PythonAsyncioRuntimeTest.eventLoopsBeyondTheConfiguredCapGetNoDedicatedLoop` | D1: admission under the event-loop cap |
| `context-python` `GraalPyContextFactoryTest.aReusableContextSharesItsEngineWithTheApplication` | G11: pool shares the bootstrap engine |
| `context-python` `GraalPyContextFactoryTest.hostClassLookupCanBeRestrictedToPackages` | D4: JDK and framework classes stay visible, other packages are blocked |
| `test-suite-python` `PythonAsyncioSpec.dataclassBodyRoundTripsThroughThePooledEventLoopContext` | C13, D3: dataclass body rebuilt in a pooled event-loop context |
| `inject-python-test` `PythonClassElementSpec` (async method returning a Python class) | C13: `getReturnType` and `getGenericReturnType` agree on `CompletionStage<Note>` |
| `context-python` `PythonConversionTest` (generator, set, dict view, sequence-protocol cases) | C12, Q26: single-pass element conversion |
| `context-python` `PythonApplicationRuntimeTest` | Q23, D2: runtime bean bound to the primary context, pool registration, compare-and-set uninstall |
| `context-python` `PythonRuntimeModuleTest` | Q18: every helper resolves from `micronaut_runtime` and is cached per context |
| `context-python` `PythonContextRuntimeStateTest` | C1: lock and shutdown gate shared between the creator context and `Value.getContext()` views |
| `context-python` `PythonPoolShutdownTest` | C2, C3: in-flight pooled work survives close; graceful shutdown keeps serving borrows |
| `context-python` `PythonAsyncioRuntimeTest` (new cases) | C3, C4: waiting borrow only fails on `close()`; host exception types survive coroutines with and without an event loop; Python failures surface as `PolyglotException`; awaited Java failures keep their message |
| `context-python` `PythonConversionFailureTest` | C5: failing element conversions throw |
| `inject-python` `PythonSourceUnitTest` | Q16: 18 `unittest` cases for the processor and transformer modules, run inside GraalPy |
| `inject-python` `AnnotationScalarsTest` | Q9: literal narrowing to declared member types |
| `inject-python-test` `UntypedDecoratorMemberSpec` | Q1: empty and one-character strings, numeric defaults on untyped decorator members |
| `context-python` `PythonClassCacheTest` | P10: class resolved once per context, not shared across contexts, dropped with the context state |
| `context-python` `GraalPyEngineFactoryTest.configuredEngineOptionsWinOverBuiltInDefaults` | P12 |
| `context-python` `PythonInvocationTest`, `PythonConversionTest` | P1, P2: `isNone` semantics (None, null, falsy values, objects whose `__str__` is "None"); direct dispatch, descriptor fallback for a shadowed method, null and empty argument arrays, no leaked execution frame |
| `inject-python` `PythonAstParserAttributeValueTest` | C6: attribute expressions are not executed at compile time; literals resolve |
| `inject-python-test` `DataclassSyncSpec` | P3, P4: unchanged fields are not written back, Python-side edits survive, direct field writes are detected, wrappers built from a Python value do not overwrite it, mutable fields still sync every call |
| `inject-python-test` `GenerateToStringEqualsSpec` (existing, golden source) | P3: classes with mutable properties generate unchanged code |

## Code quality review: stub generator, Python sources, annotation layer, util classes

Method: line and method-length metrics over `inject-python` and `context-python`, a duplicate-block scan,
dead-code scans (Java and Python), a Truffle CPU sampler run of the annotation processor on the petclinic
fixture (GraalVM CE 25.2.4), micro-timings of the `ast` passes, and one throwaway spec to confirm a
suspected defect. The items were recommendations when written and are ordered by value; the status in each row
says which were implemented since.

### Defects found on the way

| # | Status | Finding | Evidence |
|---|--------|---------|----------|
| Q1 | DONE | Untyped decorator member values are converted by guessing: a one-character string becomes `java.lang.Character`, and an empty string is dropped from the metadata entirely. Typed members (Java annotation, or a `str` annotation on the Python decorator parameter) take the typed path and are fine. | Probe spec: `@tagged(prefix="pre", letter="a", empty="")` on a bean gives `letter` of type `Character` and `empty=Optional.empty`. Cause: `GraalPyUtil.convertValueToJava(Value, VisitorContext)` returns `null` for `""` and `charAt(0)` for length-1 strings; reached from `PythonAnnotationMetadataBuilder.readAnnotationValue` when `memberType` is null. |
| Q2 | DONE | The transform pipeline parses every source three times and serialises the tree four times (`ast.parse` x3, `ast.dump` x2 to detect whether the runtime transformer changed anything, `unparse` x2). On a 49-line file the two `dump` calls alone cost 10 ms compiled, more than the parse and the visitor logic together; interpreted it is 30 to 60 times that. A `changed` flag set by the runtime transformer, plus `copy.deepcopy` of one parsed tree, removes most of it. | `PythonAstParser.getTransformSource`; timings: parse 0.8 ms, deepcopy 1.5 ms, dump 4.9 ms, unparse 4.0 ms per call; sampler: 46% of processor guest time is in `ast.py` |

Progress on the defects: Q1 is fixed in `GraalPyUtil.convertValueToJava` (strings stay strings; the typed overload
still narrows to `char`), and a second latent defect found on the way is fixed with it: `annotationMemberType`
compared a polyglot `Value` default against `Integer`, so an untyped decorator member with a numeric or boolean
default was typed as `String` and the stub generator failed with "Cannot convert '1' to String". Both are covered by
`UntypedDecoratorMemberSpec`. Q2 is done in two parts: `ast_equal` (early-exit structural comparison) replaces the
two `ast.dump` calls, and the diagnostic runtime source is produced on demand (`TransformResult.runtimeCode()` is
now lazy), which removes a parse, a transformer pass and an `unparse` per file from every production compile.

### Stub generator (`PythonStubGenerator`, 4544 lines)

| # | Finding | Recommendation |
|---|---------|----------------|
| Q3 (DONE) | `visitClass` is a single 937-line method with about 25 locally computed booleans (`isIntrospectedBean`, `extendsPythonClass`, `isReconstructibleBean`, `isFrozenDataclass`, `isAbstractIntro` computed three times under three names, ...) that steer nested lambdas up to eight levels deep. Every fix in this review touched it. | Introduce a `ClassStubModel` value object computed once (the booleans, property fields, snapshot fields, class reference) and split the body along its existing comment seams: fields, interface bridges, `Value` constructor, `asPolyglotValue`/`reconstructPolyglotValue`, factories and DI constructor, injection methods, creators, validation. Each becomes a method taking the model. |
| Q4 (DONE) | `requireField(pythonValueFinal, "Expected graalpyInternalValue field")` appeared 14 times, `pythonClassReference(element, pythonClassReference)` 12 times, `ElementQuery.ALL_METHODS...` chains 18 times. | Compute once in the model (Q3). |
| Q5 (DONE) | Eight `addBridgeMethod` overloads forward to one 160-line implementation with 11 parameters. | Replace with a `BridgeMethodSpec` builder or record; callers set only what differs. |
| Q6 (DONE) | Generated-code strings are duplicated between `PythonStubGenerator` and `PythonPooledStubGenerator` (the `ElementQuery` filter block appears in both at two places), and `normalizeAnnotationMemberName`, `rawTypeName`, `annotationClassName`, `collectAnnotationClassValues`, `isEnumMember`, `resolveAnnotationMemberType` exist in both the generator and `PythonAnnotationMetadataBuilder` with slightly different bodies. | Move annotation-value helpers into one package-private `AnnotationValues` utility used by both. |
| Q7 (DONE) | Annotation stub generation (`generateAnnotationStub`, `annotationMemberType`, `annotationDefaultValue`, `convertAnnotationMemberValue` and friends, about 500 lines) lives inside the class stub generator although it has no dependency on class stubs. | Extract `PythonAnnotationStubGenerator`. |
| Q8 (DONE) | The generator emits `putMember`, `putMembers`, `setInstanceProperty`, `newUninitializedInstance`, `newFrozenDataclassInstance`, `coercePooledValue`, `rememberPooledValue` by string name (`invokeStatic("putMembers", ...)`); a rename in the runtime compiles and fails only in `inject-python-test`. | A small `RuntimeCalls` class holding `MethodDef`-style descriptors, or at least constants, and a test that reflects over `GraalPyRuntimeUtil` to check every referenced name exists. |

Progress on Q3, Q5, Q7, Q24: `visitClass` is 145 lines of orchestration; every emission phase is its own method
(`addStateFields`, `addInterfaceAndHostBridges`, `addValueConstructors`, `addPolyglotValueMethods`,
`addFactoryMethods`, `addBridgeMethods`, `addInjectionMethods`, `addCreatorsAndPropertyAccessors`) driven by the
`ClassStubModel` record. The eight `addBridgeMethod` overloads are one method taking a `BridgeMethodSpec`. The
annotation stub generation (485 lines) lives in `PythonAnnotationStubGenerator`. `GraalPyUtil` is gone:
`PythonTypeResolver` (an instance held by `PythonVisitorContext`) resolves types and `PythonDocstrings`
renders docstrings.

Earlier progress on Q3: the three largest emission blocks (`asPolyglotValue`/`reconstructPolyglotValue`, factories and
the DI constructor, injection methods; 519 lines) are extracted verbatim into `addPolyglotValueMethods`,
`addFactoryMethods` and `addInjectionMethods`, driven by a `ClassStubModel` record that holds the 21 values
`visitClass` computes up front. `visitClass` is 423 lines. The extracted bodies still read their inputs into
locals at the top; folding those into `model.x()` calls and extracting the interface-bridging and field-emission
phases are the remaining steps.

Q4 after Q3: the fourteen `requireField(pythonValueFinal, ...)` calls are one `pythonValueField(model)` helper; the
class-reference and method-query repetition went away with the `ClassStubModel` record and the extracted phases.

### Python sources (5606 lines: processor 2899, transformer 1646, asyncio 1061)

| # | Finding | Recommendation |
|---|---------|----------------|
| Q9 (DONE) | The processor hands Java raw polyglot `Value`s inside `DecoratorDef.members` (`Map<String, Value>`), so 22 `instanceof Value` checks and the guessing conversion in Q1 live on the Java side, and the values are tied to the processor context's lifetime. | Convert at the boundary: the processor already knows literal kinds from the AST; emit a small tagged model (string, number, bool, class reference, enum reference, nested decorator, list) and build `AnnotationValue`s in Java from that, typed by the resolved member type. This deletes both `convertValueToJava` overloads (316 lines) and the `Value` handling in the builder. Note: the model is not free of polyglot values; `PythonValues` deliberately keeps a `Value` for member kinds the processor cannot tag (an arbitrary expression), so the builder still has one `Value` path. |
| Q10 (DONE) | Type annotations are flattened to strings in Python (`_extract_type_name` builds `"list[Optional[Foo]]"`) and re-parsed in Java with hand-written bracket matching (`GraalPyUtil.parseGenericType`, `parseTypeParameters`, `parseUnionTypes`, `resolveNullableUnionType`, about 250 lines). `TypeRef` already carries structured `typeArguments`, but the `String` overload of `resolvePythonTypeToJava` still has 8 call sites. | Make the processor emit structured `TypeRef`s everywhere (unions as a `TypeRef` with a marker), delete the string parser. `parseUnionTypes` is duplicated in `PythonAnnotationMetadataBuilder` as well. |
| Q11 (DONE) | "Is this Java type an annotation type / does it target ANNOTATION_TYPE" is implemented three times: `micronaut_processor.py` (170 lines, 7 functions), `micronaut_transformer.py` (150 lines, 6 methods) and, in Java, the annotation builder's own checks. Both Python copies poke `javax.lang.model` elements through interop with string comparisons (`str(kind).endswith("ANNOTATION_TYPE")`) inside layered `try/except Exception: pass`. | One Java method on `PythonVisitorContext` (`isAnnotationTypeTargetingAnnotations(ClassElement)`) that the Python side calls through the existing callback. This is the clearest "Python to Java" conversion candidate: the logic is about Java elements, not Python syntax. |
| Q12 (DONE) | The transformer generates Python decorator source as f-string templates in three places (`_generate_decorator_from_class_element`, `_generate_decorator_from_class_element_with_name`, `MicronautRuntimeTransformer.visit_Module`), each repeating the `micronaut_annotation` shim and the `_getframe(1)` trick that detects bare `@decorator` usage. | One template function; the two `_generate_decorator_from_class_element*` variants differ only in the annotation name. The `_getframe` heuristic deserves a comment and a test of its own. |
| Q13 (DONE for Java) | Python keyword aliasing (`name_` to `name`) is implemented four times: `PYTHON_KEYWORD_METHOD_ALIASES` plus `normalize_python_keyword_alias` in both Python files, and `PYTHON_KEYWORDS` sets in `PythonAstParser` and `PythonAnnotationProcessor`. | One Java `PythonKeywords` utility, exposed to Python through a binding if needed. |
| Q14 (DONE) | Defensive coding hides errors: `except Exception` 56 times and bare `except:` 8 times in the two compile-time files, most of them `pass`; 9 `print(...)` calls report errors to stdout instead of the visitor context; 31 `hasattr(visitor, '_resolve_dotted_name')`-style checks guard attributes that always exist; `ast.Str` (removed in Python 3.12) is handled in 12 places although GraalPy is 3.11+ and `ast.parse` never produces it; the `unparse` fallback for Python 3.8 in the transformer is dead. | Route diagnostics through `visitor_context.warn/fail`, narrow the excepts, delete the version shims. |
| Q15 (DONE) | Dead Python functions: `_current_class_has_external_base`, `has_python_annotation_stereotype`, `is_property_decorator` (processor); `_generate_nested_members_code`, `_is_nested_class`, `_normalize_keyword_safe_module`, `get_transformed_code` (transformer). | Delete. |
| Q16 (DONE) | The Python compile-time code has no tests of its own; it is exercised only through the Java specs. | A pytest-style file run inside GraalPy from a JUnit test (a few hundred lines of fixture-driven cases for name resolution, decorator argument mapping and type extraction) would make Q9 to Q13 safe to do. |
| Q17 (DONE) | `micronaut_asyncio.py` carried a full socket-based transport layer (`_MicronautSocketTransport`, `_MicronautDatagramTransport`, `_MicronautServer`, `_retry_socket_call` polling with a 1 ms timer, `select`-based writability checks) although the only event loop provider is the Netty one. It was also the IPv6 path: `create_connection` and `create_server` only used Netty for `AF_INET`/`AF_UNSPEC`, so `family=AF_INET6`, an IPv6 literal, `keep_alive` and pre-created sockets fell back to polled sockets. | Netty serves every address family (it resolves addresses itself; `NettyPythonAsyncioRuntimeTest` now connects and exchanges datagrams over `::1`), and the three transport classes are deleted (the module shrank from 1061 to 674 lines; the later reviews grew it back to about 900 with the transport wrappers, the server contract and argument validation). "Every address family" means Netty resolves and connects every family; the asyncio `family` argument filters the resolved addresses (see X10) and multi-host servers bind one Netty server per host. A Python socket cannot be adopted by the Netty loop with the NIO transport (only native epoll/kqueue channels can wrap a foreign descriptor), so `sock=...` and a Python socket given to `connect_accepted_socket` raise `NotImplementedError` with a message saying so, as does `keep_alive`; a loop without Netty factories rejects the networking APIs the same way. The `sock_*` coroutine helpers stay: they drive caller-owned sockets and are the documented low-level API. Docs updated. |
| Q18 (DONE) | 12 helper functions are embedded as Python text inside Java string constants (`GraalPyRuntimeUtil` 7, `PythonContextRuntime` 4, `GraalPyContextFactory` 1) and installed lazily per context through `helper()`. Several are one-liners that interop already provides: `__micronaut_put_member` is `setattr` (`Value.putMember`), `__micronaut_import_module` is `importlib.import_module`, `__micronaut_inspect_isclass` is `Value.isMetaObject()`. | Keep only the ones that need Python semantics (`object.__new__`, descriptor binding, `object.__setattr__`) and put them in one `micronaut_runtime.py` module in the VFS so they are syntax-checked and testable; replace the rest with interop calls. |

Progress on the Python sources: `PythonAnnotationTypes` (Java) now answers "is an annotation type", "targets
`ANNOTATION_TYPE`", "nested type names", "member return types" and "repeatable container" from `javax.lang.model`
or reflection; both Python files call it and no longer touch `javax.lang.model` at all (`hasattr` checks in the
transformer went from 26 to 1). `PythonKeywords` replaces the two `PYTHON_KEYWORDS` sets in Java. The dead
functions, the `ast.Str` shims, the Python 3.8 `unparse` fallback and the bare `except:` clauses are gone;
generated code that fails to parse now raises instead of printing, and the three inspection failures warn through
`warnings.warn`. `PythonSourceUnitTest` runs two `unittest` modules (18 cases) inside the processor's GraalPy
context. Q14 finished: the `ast.literal_eval` guards catch the five exceptions it documents
(`_LITERAL_EVAL_ERRORS`), reading an imported module catches `OSError`/`SyntaxError`/`ValueError`, the
`ast.unparse` fallbacks and the guards around `ClassElement` calls (`isInterface`, `isAssignable`, `members()`)
are gone. Five broad guards remain in the processor around `VisitorContext.getClassElement` calls and one in the
transformer that already warns; the generated runtime `java.type` guard is runtime code and stays.

Progress on the boundary (Q9, Q10): `PythonValues.toJava` converts every literal the processor produces at the
record boundary (`DecoratorDef.members`, `AttributeDef.value`, `ArgumentDef.defaultValue` normalise themselves in
their constructors), so the model holds `String`, `Boolean`, `Integer`/`Long`, `Double`, `List`, `Map` and nested
`DecoratorDef`s only. Both `GraalPyUtil.convertValueToJava` overloads and their helpers (567 lines) are deleted,
along with 19 `instanceof Value` branches in the builder, the generator and `PythonClassElement`; scalar narrowing
to the declared member type (`char`, `byte`, `float`, `True` on a `String` member, ...) now lives in one place,
`AnnotationScalars.coerce`, used by both the builder and the generator (a first step of Q6), and single
annotation-typed members are converted in the builder as well. Unions are structured (`TypeRef.union`, with
`isNullableUnion()` / `nonNoneMembers()`), the string-based generic and union parsers are gone (`GraalPyUtil` went
from 1280 to 427 lines), and the `String` overload of `resolvePythonTypeToJava` only resolves simple names.

Progress on Q12 and Q18: the transformer builds every generated decorator through one `_decorator_source`
template (`_generate_decorator` handles both the plain and the member-referenced variants; meta-annotation
discovery and imports are separate helpers), with the `_getframe` bare-decorator detection documented once. The
13 Python helpers that were Java string constants now live in `micronaut_runtime.py` in the application VFS;
`PythonContextRuntime.helper(context, name)` imports the module once per context (falling back to the
classpath resource for bare contexts) and caches the functions. Only the VFS bootstrap loader and the module
reload script remain as embedded sources. `PythonRuntimeModuleTest` checks every helper resolves and is cached.

### Annotation layer (`PythonAnnotationMetadataBuilder`, 1579 lines)

| # | Finding | Recommendation |
|---|---------|----------------|
| Q19 (DONE) | The builder does three unrelated jobs: the `AbstractAnnotationMetadataBuilder` contract (hierarchy, mirrors, raw values), value normalisation for every member kind (`toArray` alone is 69 lines of per-primitive loops, `collectArrayValues`, `collectEnumValues`, `collectAnnotationClassValues`, ...), and interceptor-binding synthesis (`addInterceptorBindings`, `BindingDefinition`, 140 lines). | Split into `PythonAnnotationMetadataBuilder` (contract), `PythonAnnotationValueConverter` (shared with the generator, see Q6) and `PythonInterceptorBindings`. |
| Q20 (DONE) | Alias expansion (`buildAliasedDecorators`, 80 lines) re-implemented cross-annotation `@AliasFor` resolution that `AbstractAnnotationMetadataBuilder` already performs. Tracing showed the base builder did apply every alias to the `ConfigurationReader` stereotype of `@ConfigurationProperties`; the values were lost afterwards, when the configuration reader visitor called `annotate(ConfigurationReader, prefix=...)`: a declared annotation added over an annotation the element only carried as a stereotype starts from the new members alone and shadows the stereotype in lookups, so `includes` and `excludes` disappeared. The synthesis had papered over that by declaring a `ConfigurationReader(includes, excludes)` decorator. | The builder now answers the base class's `@Aliases` lookup for members with several `@AliasFor` values (Java annotation members and Python `Annotated[...]` members alike), and `annotate` carries the stereotype's members into the declared annotation. The parallel alias logic is deleted; `AnnotationMetadataWriterSpec` and `AliasForQualifierSpec` cover the cases. |
| Q21 (resolved by Q19) | Name resolution helpers (`annotationClassName`, `decoratorAnnotationName`, `builtinAnnotationClassName`, `pythonClassName`, `resolveBinaryClassName`, `toBinaryClassName`) form a 120-line chain of string surgery on `.`/`$` separators, duplicated in part by `PythonClassElement.rawTypeName` and the generator's `rawClassName`/`rawTypeName`. | One `PythonTypeNames` utility with tests for the nested-class and keyword-package cases. |

Progress on Q19 and Q6: `PythonAnnotationMetadataBuilder` (937 lines) keeps the builder contract, alias
expansion and member resolution; `PythonAnnotationValues` (418) normalises member values and
`PythonInterceptorBindings` (211) synthesises interceptor bindings, both behind the small `AnnotationLookups`
interface the builder implements. `AnnotationNames` holds the helpers that were copied between the builder, the
stub generators and `PythonClassElement`.

Progress on Q22 and Q8: `GraalPyRuntimeUtil` is gone. `PythonCoercion` (Java to Python, pooled values, async
members), `PythonConversion` (Python to Java), `PythonHttpConversion` (HTTP responses and publishers, the only
class touching `micronaut-http`) and `PythonInvocation` (bridge dispatch and descriptors) replace it; the
language id lives in `PythonContextRuntime.PYTHON`. The generator references each class through its own
`ClassTypeDef` constant, so a runtime rename shows up as a compile error in the generator's tests rather than in a
generated stub.

Progress on Q23 and D2: `PythonContextRuntime` (about 1000 lines) keeps only the generated-code entry points and the
class, script and helper resolution. `PythonContextRegistry` holds the per-context state, execution counters,
execution frames and the idle/close listeners (about 450 lines, moved verbatim). `PythonApplicationRuntime` holds the
primary context, class loader, pool and offload executor of one application and is a bean created by
`GraalPyContextFactory`; `resetContext` uninstalls that object instead of nulling four static fields.
`PythonApplicationRuntimeTest` covers the bean binding, the pool registration and the compare-and-set uninstall.
`PythonAsyncioRuntime` keeps its own static `RuntimeState` record (enabled flag, event-loop providers, executor);
it is replaced as one value under the class monitor and is left as is.

Q21 after Q19: `annotationClassName`, `decoratorAnnotationName`, `builtinAnnotationClassName` and `pythonClassName`
are gone; `toBinaryClassName` asks the Java visitor context for the binary name and caches it, and the only
string helpers left are `toQualifiedPythonName` (package plus class) and `AnnotationNames.rawTypeName`, neither
duplicated elsewhere. A separate `PythonTypeNames` utility would have one method, so none is added.

### Util classes

| # | Finding | Recommendation |
|---|---------|----------------|
| Q22 (DONE) | `GraalPyRuntimeUtil` (1537 lines, 32 public static methods) mixes Java-to-Python coercion, Python-to-Java conversion, pooled-value bookkeeping (a `ScopedValue` conversion registry), async member adaptation, publisher and `HttpResponse` conversion, enum handling and datetime helpers. It also imports `micronaut-http` through a `compileOnlyApi` dependency, so the runtime module's public API references classes that may be absent. | Split into `PythonCoercion` (to Python), `PythonConversion` (to Java), `PooledValues`, `PythonAsyncMembers` and move the HTTP conversion into a `context-python-http` package or behind a `TargetTypeMapping`. What was done: `PythonCoercion`, `PythonConversion`, `PythonHttpConversion` and `ValueCoercibles` replace the utility; pooled-value bookkeeping stayed in `PythonCoercion` and the async member handling in `PythonContextRuntime` rather than the `PooledValues`/`PythonAsyncMembers` classes first proposed, and the HTTP conversion stays in `context-python` behind `compileOnlyApi(micronaut-http)` since a separate module was not worth its own artifact. The facade was not kept: the module is unreleased, so no generated code outside this repository calls `GraalPyRuntimeUtil`, and the generator now emits calls to the new classes. |
| Q23 (DONE) | `PythonContextRuntime` (1730 lines) is a static god class: global context registry and execution counters, pooled execution facade (`withPooled`, `invokePooled`, `injectPooledScript`), instance creation (`newInstance`, `newIntroduction`, `newFrozenDataclassInstance`, four `...WithDefaultedTrailingNulls` variants), class lookup, helper installation, reset and reuse flags. Static state was the root cause of two bugs fixed earlier (C1, C10). | Turn the registry into a `PythonRuntime` bean owned by the `ApplicationContext` (design item D2) and keep static entry points only for generated code, delegating to the current runtime. |
| Q24 (DONE) | `GraalPyUtil` (1280 lines) in `inject-python` is two things: type resolution from Python annotations to `ClassElement`s (about 500 lines) and polyglot value conversion for annotation members (about 400 lines, see Q1, Q9). `ObjectHelper` is a well-scoped 238-line class used by one caller. | After Q9 and Q10, what remains of `GraalPyUtil` is the type resolver; rename it `PythonTypeResolver` and make it an instance held by `PythonVisitorContext` so the `boundGenerics` map stops being passed through every call. |
| Q25 (DONE) | `ValueCoercible` is an interface with 457 lines including four static `hostObject` overloads and `ProxyObject` default methods with real logic. | Move the statics to a `ValueCoercibles` utility; keep the interface declarative. |
| Q26 (DONE for the runtime probes) | Exception handling: 129 `catch (X ignored)` or empty-body catches across the two modules, 7 in `PythonAsyncioRuntime`, 6 each in `PythonAnnotationProcessor` and `GraalPyRuntimeUtil`. `getSize`/`getElementAt` in the runtime still use exceptions for control flow (`getArrayElement` inside `try` as a type probe). | Replace probes with `hasArrayElements()`/`hasHashEntries()` checks (partly done in P6) and log or rethrow the rest. |
| Q28 (DONE) | Of the Python that remains after Q11, the transformer's Java-type queries were the last code that probed `ClassElement`s from Python: `_get_nested_class_elements` walked every member of an annotation (`getMethods`, `getReturnType`, `isArray`, `fromArray`, `getName` per member) and `_generate_nested_members_sections` asked each nested type for its name, annotation-ness and repeatable container, about forty interop crossings per imported annotation; `_is_java_throwable_base` and `_java_class_name` probed base classes the same way. The AST visitors themselves, the runtime helpers and the asyncio loop must stay Python (see the rewrite assessment below). | `PythonAnnotationTypes.nestedTypes` answers the nested types of an annotation in one call as `NestedType` records (element, name, member name, annotation, repeatable container); `PythonJavaTypes.isThrowable` and `isConcreteClass` answer the base-class questions, including the reflection-backed elements tests use. The transformer no longer calls `getMethods`/`getReturnType` at all. `PythonJavaTypesTest` covers the helpers. |
| Q27 (DONE) | Naming and packaging: `GraalPyUtil` and `GraalPyRuntimeUtil` differ only by module; `ObjectHelper` is generic; the `visitor` package holds both the AST data model (`ClassDef`, `FunctionDef`, `TypeRef`, ...) and the `ClassElement` implementations. | Split `processing.visitor` into `processing.model` (records) and `processing.element` (elements). |

Progress on Q25 and Q26: the four `hostObject` overloads, the two `rawHostObject` helpers and `matchesArgument`
moved from the `ValueCoercible` interface to the `ValueCoercibles` utility; the interface keeps the contract, the
proxy default methods and the `HostObjectReference` marker. `getSize`/`getElementAt` in `PythonConversion`, which
probed the array protocol with `getArrayElement` inside `try`, iterated with `__iter__`/`__next__` until an exception
and swallowed everything, are replaced by `convertElements` over the interop iterator (see C12).

Progress on Q27: `processing.model` holds the fifteen source-model records and sealed interfaces (`ClassDef`,
`FunctionDef`, `DecoratorDef`, `TypeRef`, ...), which depend on nothing but each other; `processing.element` holds
the seventeen `ClassElement` implementations and the element factory; `processing.visitor` keeps the visitor context,
the type-element visitor processor, the loaded-visitor wrapper and the script-element processor service (its
`META-INF/services` file name is unchanged). The Python processor's `java.type` references point at the model package.
No member had to become public: the elements only reach the visitor context through its public API.

Rewrite assessment (what else could move to Java): the remaining Python is 2655 lines of processor, 1288 of
transformer, 141 of runtime helpers and about 900 of asyncio loop. The processor and the transformer's `visit_*` methods
walk Python's own `ast` tree, for which Java has no parser; driving that tree from Java would multiply interop
crossings rather than remove them, and that walk is where compile time goes (only a JIT-capable build JDK changes
it, per P7). The runtime helpers exist to do what only Python can (allocate without `__init__`, assign past an
overridden `__setattr__`), and the asyncio module implements Python's `AbstractEventLoop` contract. Q28 was the
last move worth making.

### Suggested order

1. Q1 and Q2 (a bug and a measurable compile-time win, both small).
2. Q11, Q13, Q15, Q14 (delete duplicated and dead Python, move the Java-element logic to Java) with Q16 as the safety net.
3. Q9 and Q10 (typed boundary), which then allow Q19 and Q24.
4. Q3 to Q8 (generator structure) once the boundary is stable, since the generator consumes the model.
5. Q22 and Q23 alongside design item D2.
