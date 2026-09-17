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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protocol tests of {@code as_async_iterable} and {@code as_publisher}: demand, ordering,
 * cancellation, errors and the threads the Python side runs on.
 */
final class PythonAsyncioStreamsTest {

    private ThreadEventLoop eventLoop;
    private Context context;

    @BeforeEach
    void setUp() {
        eventLoop = new ThreadEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        // a plain context has no GraalPy virtual file system: load the module from the classpath
        PythonAsyncioRuntime.asyncioHelper(context, "as_publisher");
    }

    @AfterEach
    void tearDown() {
        PythonAsyncioRuntime.setEventLoopProviders(List.of());
        context.close(true);
        eventLoop.close();
    }

    // ---- Java publisher -> Python async for ----

    @Test
    void asyncForConsumesAnOrderedStreamWithoutDropsOrDuplicates() throws Exception {
        context.getBindings(PYTHON).putMember("source", Flux.range(1, 1000));
        Object result = run("""
            from micronaut_asyncio import as_async_iterable
            async def consume():
                seen = []
                async with as_async_iterable(source) as items:
                    async for item in items:
                        seen.append(item)
                return seen
            consume()
            """);
        Value seen = Value.asValue(result);
        assertEquals(1000, seen.getArraySize());
        for (int i = 0; i < 1000; i++) {
            assertEquals(i + 1, seen.getArrayElement(i).asInt());
        }
    }

    @Test
    void everyIterationRequestsExactlyOneItemAndCallbacksRunOnTheLoop() throws Exception {
        ControllablePublisher publisher = new ControllablePublisher();
        context.getBindings(PYTHON).putMember("source", publisher);
        CompletionStage<?> stage = start("""
            import java
            from micronaut_asyncio import as_async_iterable
            Thread = java.type("java.lang.Thread")
            threads = []
            async def consume():
                items = as_async_iterable(source)
                first = await items.__anext__()
                threads.append(Thread.currentThread().getName())
                second = await items.__anext__()
                await items.aclose()
                return [first, second]
            consume()
            """);
        assertTrue(publisher.subscribed.await(5, TimeUnit.SECONDS), "the first __anext__ subscribes");
        publisher.awaitRequested(1);
        assertEquals(1, publisher.requested.get(), "one request per pending __anext__");
        publisher.emit("a");
        publisher.awaitRequested(2);
        publisher.emit("b");
        assertTrue(publisher.cancelled.await(5, TimeUnit.SECONDS), "aclose cancels the subscription");
        Value result = Value.asValue(stage.toCompletableFuture().get(10, TimeUnit.SECONDS));
        assertEquals("a", result.getArrayElement(0).asString());
        assertEquals("b", result.getArrayElement(1).asString());
        assertEquals(2, publisher.requested.get());
        String consumerThread = context.getBindings(PYTHON).getMember("threads").getArrayElement(0).asString();
        assertTrue(consumerThread.contains("python-stream-loop"), consumerThread);
    }

    @Test
    void breakingOutOfTheContextManagerCancelsUpstreamOnce() throws Exception {
        ControllablePublisher publisher = new ControllablePublisher();
        context.getBindings(PYTHON).putMember("source", publisher);
        CompletionStage<?> stage = start("""
            from micronaut_asyncio import as_async_iterable
            async def consume():
                async with as_async_iterable(source) as items:
                    async for item in items:
                        if item == "stop":
                            break
                return "done"
            consume()
            """);
        publisher.awaitRequested(1);
        publisher.emit("stop");
        assertEquals("done", stage.toCompletableFuture().get(10, TimeUnit.SECONDS));
        assertTrue(publisher.cancelled.await(5, TimeUnit.SECONDS));
        assertEquals(1, publisher.cancelCount.get());
        assertEquals(1, publisher.requested.get(), "no further request after the break");
    }

    @Test
    void completionAfterTheFinalItemEndsTheIterationAfterDrainingIt() throws Exception {
        Publisher<String> publisher = subscriber -> subscriber.onSubscribe(new Subscription() {
            private boolean emitted;

            @Override
            public void request(long n) {
                if (!emitted) {
                    emitted = true;
                    // the final item and the completion in one synchronous request
                    subscriber.onNext("last");
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                // nothing to release: the items were emitted synchronously
            }
        });
        context.getBindings(PYTHON).putMember("source", publisher);
        Object result = run("""
            from micronaut_asyncio import as_async_iterable
            async def consume():
                seen = []
                async for item in as_async_iterable(source):
                    seen.append(item)
                return seen
            consume()
            """);
        Value seen = Value.asValue(result);
        assertEquals(1, seen.getArraySize());
        assertEquals("last", seen.getArrayElement(0).asString());
    }

    @Test
    void emptyPublisherEndsTheIterationImmediately() throws Exception {
        context.getBindings(PYTHON).putMember("source", Flux.empty());
        Object result = run("""
            from micronaut_asyncio import as_async_iterable
            async def consume():
                count = 0
                async for item in as_async_iterable(source):
                    count += 1
                return count
            consume()
            """);
        assertEquals(0, Value.asValue(result).asInt());
    }

    @Test
    void publisherErrorSurfacesAsAPythonExceptionCarryingTheJavaCause() throws Exception {
        context.getBindings(PYTHON).putMember("source", Flux.concat(Flux.just("one"), Flux.error(new IllegalStateException("upstream failed"))));
        Object result = run("""
            from micronaut_asyncio import as_async_iterable, MicronautJavaException
            async def consume():
                seen = []
                try:
                    async for item in as_async_iterable(source):
                        seen.append(item)
                except MicronautJavaException as failure:
                    seen.append(failure.java_exception.getMessage())
                return seen
            consume()
            """);
        Value seen = Value.asValue(result);
        assertEquals("one", seen.getArrayElement(0).asString());
        assertEquals("upstream failed", seen.getArrayElement(1).asString());
    }

    @Test
    void cancellingTheConsumerTaskDuringAnextCancelsTheSubscription() throws Exception {
        ControllablePublisher publisher = new ControllablePublisher();
        context.getBindings(PYTHON).putMember("source", publisher);
        Object result = run("""
            import asyncio
            from micronaut_asyncio import as_async_iterable
            async def consume():
                items = as_async_iterable(source)
                async def wait_for_item():
                    return await items.__anext__()
                task = asyncio.get_running_loop().create_task(wait_for_item())
                await asyncio.sleep(0.05)
                task.cancel()
                try:
                    await task
                except asyncio.CancelledError:
                    pass
                return "cancelled=" + str(task.cancelled())
            consume()
            """);
        assertEquals("cancelled=True", result);
        assertTrue(publisher.cancelled.await(5, TimeUnit.SECONDS));
        assertEquals(1, publisher.cancelCount.get());
    }

    @Test
    void cancellingTheCoroutineStageCancelsTheSubscription() throws Exception {
        ControllablePublisher publisher = new ControllablePublisher();
        context.getBindings(PYTHON).putMember("source", publisher);
        CompletionStage<?> stage = start("""
            from micronaut_asyncio import as_async_iterable
            async def consume():
                async with as_async_iterable(source) as items:
                    async for item in items:
                        pass
            consume()
            """);
        publisher.awaitRequested(1);
        stage.toCompletableFuture().cancel(true);
        assertTrue(publisher.cancelled.await(5, TimeUnit.SECONDS), "a cancelled request cancels the stream");
    }

    @Test
    void aPublisherEmittingMoreThanRequestedIsCancelledAndFailsTheIteration() throws Exception {
        Publisher<String> misbehaving = subscriber -> subscriber.onSubscribe(new Subscription() {
            private boolean emitted;

            @Override
            public void request(long n) {
                if (!emitted) {
                    emitted = true;
                    subscriber.onNext("one");
                    subscriber.onNext("two");
                    subscriber.onNext("three");
                }
            }

            @Override
            public void cancel() {
                context.getBindings(PYTHON).putMember("upstream_cancelled", true);
            }
        });
        context.getBindings(PYTHON).putMember("source", misbehaving);
        Object result = run("""
            from micronaut_asyncio import as_async_iterable
            async def consume():
                seen = []
                try:
                    async for item in as_async_iterable(source):
                        seen.append(item)
                except RuntimeError as failure:
                    seen.append(str(failure))
                return seen
            consume()
            """);
        Value seen = Value.asValue(result);
        assertEquals("one", seen.getArrayElement(0).asString());
        assertEquals("two", seen.getArrayElement(1).asString(), "the one buffered item is drained");
        assertTrue(seen.getArrayElement(2).asString().contains("more items than were requested"), seen.toString());
        assertTrue(context.getBindings(PYTHON).getMember("upstream_cancelled").asBoolean());
    }

    @Test
    void overlappingAnextCallsAreRefused() throws Exception {
        ControllablePublisher publisher = new ControllablePublisher();
        context.getBindings(PYTHON).putMember("source", publisher);
        Object result = run("""
            import asyncio
            from micronaut_asyncio import as_async_iterable
            async def consume():
                items = as_async_iterable(source)
                first = asyncio.get_running_loop().create_task(items.__anext__())
                await asyncio.sleep(0)
                try:
                    await items.__anext__()
                    return "accepted"
                except RuntimeError as failure:
                    return str(failure)
                finally:
                    first.cancel()
            consume()
            """);
        assertTrue(result.toString().contains("previous __anext__ is still pending"), result.toString());
    }

    @Test
    void asyncIterableOutsideAnyLoopIsRefusedWithAClearMessage() {
        PythonAsyncioRuntime.setEventLoopProviders(List.of());
        context.getBindings(PYTHON).putMember("source", Flux.just("x"));
        PolyglotException failure = assertThrows(PolyglotException.class, () -> context.eval(PYTHON, """
            from micronaut_asyncio import as_async_iterable
            as_async_iterable(source)
            """));
        assertTrue(failure.getMessage().contains("Micronaut asyncio runtime"), failure.getMessage());
    }

    @Test
    void asyncIterableRejectsAValueThatIsNotAPublisher() {
        PolyglotException failure = assertThrows(PolyglotException.class, () -> context.eval(PYTHON, """
            from micronaut_asyncio import as_async_iterable
            as_async_iterable("not a publisher")
            """));
        assertTrue(failure.getMessage().contains("expects a Java Publisher"), failure.getMessage());
    }

    // ---- Python async generator -> Java publisher ----

    @Test
    void generatorPublisherDeliversAnOrderedStreamOnDemand() throws Exception {
        Publisher<?> publisher = publisher("""
            from micronaut_asyncio import as_publisher
            async def numbers():
                for value in range(1000):
                    yield value
            as_publisher(numbers)
            """);
        List<?> items = Flux.from(publisher).collectList().block(java.time.Duration.ofSeconds(30));
        assertNotNull(items);
        assertEquals(1000, items.size());
        for (int i = 0; i < 1000; i++) {
            assertEquals(i, ((Number) items.get(i)).intValue());
        }
        // the driver releases the execution after signalling completion, so it may still be unwinding
        awaitReleased();
    }

    @Test
    void zeroDemandNeverAdvancesTheGeneratorAndIncrementalDemandProducesExactlyThatMany() throws Exception {
        Publisher<?> publisher = publisher("""
            from micronaut_asyncio import as_publisher
            advances = []
            async def numbers():
                value = 0
                while True:
                    advances.append(value)
                    yield value
                    value += 1
            as_publisher(numbers)
            """);
        ProbeSubscriber probe = new ProbeSubscriber();
        publisher.subscribe(probe);
        assertTrue(probe.subscribed.await(5, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertEquals(0, advances(), "no demand, no advance");
        assertEquals(0, probe.items.size());

        probe.subscription.request(2);
        probe.awaitItems(2);
        Thread.sleep(100);
        assertEquals(2, advances(), "exactly the requested number of advances");
        assertEquals(List.of(0, 1), probe.intItems());

        probe.subscription.request(3);
        probe.awaitItems(5);
        Thread.sleep(100);
        assertEquals(5, advances());
        assertEquals(List.of(0, 1, 2, 3, 4), probe.intItems());
        assertFalse(probe.completed.get());
        assertEquals(1, activeExecutions(), "an open stream is an active execution");

        probe.subscription.cancel();
        awaitReleased();
        assertEquals(0, activeExecutions());
    }

    @Test
    void cancellationRunsTheGeneratorsFinallyBlockAndReleasesTheStream() throws Exception {
        Publisher<?> publisher = publisher("""
            import asyncio
            from micronaut_asyncio import as_publisher
            events = []
            async def slow():
                try:
                    yield "first"
                    events.append("awaiting")
                    await asyncio.sleep(30)
                    yield "never"
                finally:
                    events.append("finally")
            as_publisher(slow)
            """);
        ProbeSubscriber probe = new ProbeSubscriber();
        publisher.subscribe(probe);
        probe.subscription.request(2);
        probe.awaitItems(1);
        awaitEvent("awaiting");
        probe.subscription.cancel();
        awaitEvent("finally");
        awaitReleased();
        assertEquals(List.of("first"), probe.items);
        assertFalse(probe.completed.get());
        assertNull(probe.error.get());
    }

    @Test
    void exhaustionCompletesOnceAndTheGeneratorCleansUp() throws Exception {
        Publisher<?> publisher = publisher("""
            from micronaut_asyncio import as_publisher
            events = []
            async def two():
                try:
                    yield "a"
                    yield "b"
                finally:
                    events.append("finally")
            as_publisher(two)
            """);
        ProbeSubscriber probe = new ProbeSubscriber();
        publisher.subscribe(probe);
        probe.subscription.request(Long.MAX_VALUE);
        assertTrue(probe.terminated.await(10, TimeUnit.SECONDS));
        assertEquals(List.of("a", "b"), probe.items);
        assertTrue(probe.completed.get());
        assertEquals(1, probe.completions.get());
        awaitEvent("finally");
        awaitReleased();
    }

    @Test
    void aFailingGeneratorSignalsOnErrorWithThePythonFailure() throws Exception {
        Publisher<?> publisher = publisher("""
            from micronaut_asyncio import as_publisher
            async def failing():
                yield "a"
                raise ValueError("generator failed")
            as_publisher(failing)
            """);
        ProbeSubscriber probe = new ProbeSubscriber();
        publisher.subscribe(probe);
        probe.subscription.request(10);
        assertTrue(probe.terminated.await(10, TimeUnit.SECONDS));
        assertEquals(List.of("a"), probe.items);
        Throwable error = probe.error.get();
        assertInstanceOf(PolyglotException.class, error);
        assertTrue(error.getMessage().contains("generator failed"), error.getMessage());
        assertTrue(((PolyglotException) error).isGuestException());
        awaitReleased();
    }

    @Test
    void aJavaExceptionRaisedInTheGeneratorKeepsItsType() throws Exception {
        context.getBindings(PYTHON).putMember("failure", new IllegalStateException("java failure"));
        Publisher<?> publisher = publisher("""
            from micronaut_asyncio import as_publisher
            async def failing():
                yield "a"
                raise failure
            as_publisher(failing)
            """);
        ProbeSubscriber probe = new ProbeSubscriber();
        publisher.subscribe(probe);
        probe.subscription.request(10);
        assertTrue(probe.terminated.await(10, TimeUnit.SECONDS));
        Throwable error = probe.error.get();
        assertInstanceOf(IllegalStateException.class, error);
        assertEquals("java failure", error.getMessage());
    }

    @Test
    void aNoneElementFailsTheStreamAndClosesTheGenerator() throws Exception {
        Publisher<?> publisher = publisher("""
            from micronaut_asyncio import as_publisher
            events = []
            async def nulls():
                try:
                    yield "a"
                    yield None
                    yield "c"
                finally:
                    events.append("finally")
            as_publisher(nulls)
            """);
        ProbeSubscriber probe = new ProbeSubscriber();
        publisher.subscribe(probe);
        probe.subscription.request(10);
        assertTrue(probe.terminated.await(10, TimeUnit.SECONDS));
        assertEquals(List.of("a"), probe.items);
        Throwable error = probe.error.get();
        assertNotNull(error);
        assertTrue(error.getMessage().contains("yielded None"), error.getMessage());
        awaitEvent("finally");
        awaitReleased();
    }

    @Test
    void nonPositiveDemandIsAReactiveStreamsViolation() throws Exception {
        Publisher<?> publisher = publisher("""
            from micronaut_asyncio import as_publisher
            advances = []
            async def numbers():
                advances.append(1)
                yield 1
            as_publisher(numbers)
            """);
        ProbeSubscriber probe = new ProbeSubscriber();
        publisher.subscribe(probe);
        probe.subscription.request(0);
        assertTrue(probe.terminated.await(10, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, probe.error.get());
        assertTrue(probe.error.get().getMessage().contains("3.9"));
        Thread.sleep(100);
        assertEquals(0, advances());
        awaitReleased();
    }

    @Test
    void demandSaturatesInsteadOfOverflowing() throws Exception {
        Publisher<?> publisher = publisher("""
            from micronaut_asyncio import as_publisher
            async def three():
                yield 1
                yield 2
                yield 3
            as_publisher(three)
            """);
        ProbeSubscriber probe = new ProbeSubscriber();
        publisher.subscribe(probe);
        probe.subscription.request(Long.MAX_VALUE);
        probe.subscription.request(Long.MAX_VALUE);
        assertTrue(probe.terminated.await(10, TimeUnit.SECONDS));
        assertEquals(List.of(1, 2, 3), probe.intItems());
        assertTrue(probe.completed.get());
    }

    @Test
    void eachSubscriptionGetsItsOwnGeneratorInstance() throws Exception {
        Publisher<?> publisher = publisher("""
            from micronaut_asyncio import as_publisher
            created = []
            async def numbers():
                created.append(len(created))
                yield len(created)
                yield len(created)
            as_publisher(numbers)
            """);
        ProbeSubscriber first = new ProbeSubscriber();
        ProbeSubscriber second = new ProbeSubscriber();
        publisher.subscribe(first);
        publisher.subscribe(second);
        first.subscription.request(10);
        second.subscription.request(10);
        assertTrue(first.terminated.await(10, TimeUnit.SECONDS));
        assertTrue(second.terminated.await(10, TimeUnit.SECONDS));
        assertEquals(2, first.items.size());
        assertEquals(2, second.items.size());
        assertEquals(2, context.getBindings(PYTHON).getMember("created").getArraySize());
    }

    @Test
    void anAsyncIteratorObjectCanBeSubscribedToOnce() throws Exception {
        Publisher<?> publisher = publisher("""
            from micronaut_asyncio import as_publisher
            async def numbers():
                yield 1
            as_publisher(numbers())
            """);
        ProbeSubscriber first = new ProbeSubscriber();
        publisher.subscribe(first);
        first.subscription.request(10);
        assertTrue(first.terminated.await(10, TimeUnit.SECONDS));
        assertEquals(List.of(1), first.intItems());
        ProbeSubscriber second = new ProbeSubscriber();
        publisher.subscribe(second);
        second.subscription.request(1);
        assertTrue(second.terminated.await(10, TimeUnit.SECONDS));
        assertNotNull(second.error.get());
        assertTrue(second.error.get().getMessage().contains("subscribed to once"), second.error.get().getMessage());
    }

    @Test
    void subscriberCancellingFromOnNextStopsTheGenerator() throws Exception {
        Publisher<?> publisher = publisher("""
            from micronaut_asyncio import as_publisher
            advances = []
            async def numbers():
                value = 0
                while True:
                    advances.append(value)
                    yield value
                    value += 1
            as_publisher(numbers)
            """);
        ProbeSubscriber probe = new ProbeSubscriber() {
            @Override
            public void onNext(Object item) {
                super.onNext(item);
                subscription.cancel();
            }
        };
        publisher.subscribe(probe);
        probe.subscription.request(Long.MAX_VALUE);
        probe.awaitItems(1);
        awaitReleased();
        Thread.sleep(100);
        assertEquals(List.of(0), probe.intItems());
        assertEquals(1, advances());
    }

    @Test
    void theGeneratorRunsOnTheEventLoopThreadEvenWhenSubscribedElsewhere() throws Exception {
        Publisher<?> publisher = publisher("""
            import java
            from micronaut_asyncio import as_publisher
            Thread = java.type("java.lang.Thread")
            async def names():
                yield Thread.currentThread().getName()
            as_publisher(names)
            """);
        AtomicReference<Object> item = new AtomicReference<>();
        Thread subscribing = new Thread(() -> item.set(Flux.from(publisher).blockFirst(java.time.Duration.ofSeconds(10))), "subscriber-thread");
        subscribing.start();
        subscribing.join(TimeUnit.SECONDS.toMillis(15));
        assertNotNull(item.get());
        assertTrue(item.get().toString().contains("python-stream-loop"), item.get().toString());
    }

    @Test
    void asPublisherOutsideAnyLoopIsRefusedWithAClearMessage() {
        PythonAsyncioRuntime.setEventLoopProviders(List.of());
        PolyglotException failure = assertThrows(PolyglotException.class, () -> context.eval(PYTHON, """
            from micronaut_asyncio import as_publisher
            async def numbers():
                yield 1
            as_publisher(numbers)
            """));
        assertTrue(failure.getMessage().contains("Micronaut asyncio runtime"), failure.getMessage());
    }

    @Test
    void asPublisherRejectsASynchronousGenerator() {
        PolyglotException failure = assertThrows(PolyglotException.class, () -> context.eval(PYTHON, """
            from micronaut_asyncio import as_publisher
            def numbers():
                yield 1
            as_publisher(numbers())
            """));
        assertTrue(failure.getMessage().contains("async generator function or an async iterable"), failure.getMessage());
    }

    @Test
    void toPublisherExposesAnAsyncGeneratorObjectReturnedByABridgeMethod() throws Exception {
        Value generator = context.eval(PYTHON, """
            async def numbers():
                yield "x"
                yield "y"
            numbers()
            """);
        Publisher<?> publisher = PythonAsyncioRuntime.toPublisher(generator);
        List<?> items = Flux.from(publisher).collectList().block(java.time.Duration.ofSeconds(10));
        assertEquals(List.of("x", "y"), items);
    }

    @Test
    void streamsRoundTripBetweenTheTwoDirections() throws Exception {
        Value make = context.eval(PYTHON, """
            from micronaut_asyncio import as_publisher, as_async_iterable
            async def doubled(source):
                async with as_async_iterable(source) as items:
                    async for item in items:
                        yield item * 2
            def make(source):
                return as_publisher(lambda: doubled(source))
            make
            """);
        Publisher<?> doubled = make.execute(Flux.range(1, 5)).asHostObject();
        List<?> items = Flux.from(doubled).collectList().block(java.time.Duration.ofSeconds(10));
        assertNotNull(items);
        assertEquals(List.of(2, 4, 6, 8, 10), items.stream().map(item -> ((Number) item).intValue()).toList());
        awaitReleased();
    }

    // ---- helpers ----

    private Object run(String source) throws Exception {
        try {
            return start(source).toCompletableFuture().get(20, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    private CompletionStage<?> start(String source) {
        Value coroutine = context.eval(PYTHON, source);
        return PythonAsyncioRuntime.toCompletionStage(coroutine);
    }

    private Publisher<?> publisher(String source) {
        return context.eval(PYTHON, source).asHostObject();
    }

    private int advances() {
        return (int) context.getBindings(PYTHON).getMember("advances").getArraySize();
    }

    private void awaitEvent(String event) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Value events = context.getBindings(PYTHON).getMember("events");
            for (int i = 0; i < events.getArraySize(); i++) {
                if (event.equals(events.getArrayElement(i).asString())) {
                    return;
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Event [" + event + "] did not happen: " + context.getBindings(PYTHON).getMember("events"));
    }

    private int activeExecutions() {
        return PythonContextRegistry.activeExecutions(context);
    }

    private void awaitReleased() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (activeExecutions() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(0, activeExecutions(), "the stream did not release its execution");
    }

    /**
     * A publisher the test drives by hand: it records demand and cancellation and emits on request.
     */
    private static final class ControllablePublisher implements Publisher<Object> {
        final CountDownLatch subscribed = new CountDownLatch(1);
        final CountDownLatch cancelled = new CountDownLatch(1);
        final AtomicInteger cancelCount = new AtomicInteger();
        final AtomicLong requested = new AtomicLong();
        private volatile Subscriber<? super Object> subscriber;

        @Override
        public void subscribe(Subscriber<? super Object> subscriber) {
            this.subscriber = subscriber;
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    requested.addAndGet(n);
                }

                @Override
                public void cancel() {
                    cancelCount.incrementAndGet();
                    cancelled.countDown();
                }
            });
            subscribed.countDown();
        }

        void emit(Object item) {
            subscriber.onNext(item);
        }

        void awaitRequested(long expected) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (requested.get() < expected && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertTrue(requested.get() >= expected, "expected " + expected + " requests, saw " + requested.get());
        }
    }

    private static class ProbeSubscriber implements Subscriber<Object> {
        final List<Object> items = new CopyOnWriteArrayList<>();
        final CountDownLatch subscribed = new CountDownLatch(1);
        final CountDownLatch terminated = new CountDownLatch(1);
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicInteger completions = new AtomicInteger();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        volatile Subscription subscription;

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            subscribed.countDown();
        }

        @Override
        public void onNext(Object item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            error.set(throwable);
            terminated.countDown();
        }

        @Override
        public void onComplete() {
            completed.set(true);
            completions.incrementAndGet();
            terminated.countDown();
        }

        List<Integer> intItems() {
            List<Integer> result = new ArrayList<>();
            for (Object item : items) {
                result.add(((Number) item).intValue());
            }
            return result;
        }

        void awaitItems(int count) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (items.size() < count && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertEquals(count, items.size(), "items: " + items);
        }
    }

    /**
     * A real single-threaded loop: signals from other threads must be marshalled onto it.
     */
    private static final class ThreadEventLoop implements PythonEventLoop, AutoCloseable {
        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "python-stream-loop"));
        private volatile Thread thread;

        ThreadEventLoop() {
            CompletableFuture<Void> started = new CompletableFuture<>();
            executor.execute(() -> {
                thread = Thread.currentThread();
                started.complete(null);
            });
            started.join();
        }

        @Override
        public boolean inEventLoop() {
            return Thread.currentThread() == thread;
        }

        @Override
        public void execute(Runnable runnable) {
            executor.execute(runnable);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
            return executor.schedule(runnable, delay, unit);
        }

        @Override
        public double time() {
            return System.nanoTime() / 1_000_000_000.0d;
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }
}
