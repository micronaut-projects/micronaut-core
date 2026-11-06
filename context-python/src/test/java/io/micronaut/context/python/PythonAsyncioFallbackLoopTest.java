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

import io.micronaut.context.ApplicationContext;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.NonBlocking;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coroutines started without a Micronaut event loop ({@code micronaut-context-python-netty} absent):
 * they are driven by a Python loop, which must neither block a Netty event loop nor start Python threads.
 */
final class PythonAsyncioFallbackLoopTest {

    @Test
    void runInExecutorUsesTheBlockingExecutorInsteadOfPythonThreads() throws Exception {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            Context context = applicationContext.getBean(Context.class);
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                import java
                Thread = java.type("java.lang.Thread")
                async def offload():
                    loop = asyncio.get_running_loop()
                    caller = Thread.currentThread().getName()
                    worker = await loop.run_in_executor(None, lambda: Thread.currentThread().getName())
                    both = await asyncio.gather(
                        loop.run_in_executor(None, lambda: 1),
                        loop.run_in_executor(None, lambda: 2),
                    )
                    return caller + "|" + worker + "|" + str(sum(both))
                offload
                """).execute();

            CompletionStage<?> stage = PythonAsyncioRuntime.toCompletionStage(coroutine);
            String[] result = stage.toCompletableFuture().get(10, TimeUnit.SECONDS).toString().split("\\|");

            // the context refuses Python threads: the callable ran on Micronaut's blocking executor
            assertNotEquals(result[0], result[1]);
            assertTrue(result[1].startsWith("io-executor-thread"), result[1]);
            assertEquals("3", result[2]);
        }
    }

    @Test
    void coroutineStartedOnNonBlockingThreadWithoutEventLoopDoesNotBlockIt() throws Exception {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            Context context = applicationContext.getBean(Context.class);
            // the stage a client call on this very event loop would complete: only the event loop can complete it
            CompletableFuture<String> response = new CompletableFuture<>();
            Value target = context.eval(PYTHON, """
                class Target:
                    pass
                Target()
                """);
            PythonCoercion.putMember(target, "client", PythonCoercion.asyncMemberValue(target, new DeferredClient(response)));
            Value coroutine = context.eval(PYTHON, """
                import java
                Thread = java.type("java.lang.Thread")
                async def call(target):
                    return Thread.currentThread().getName() + "|" + await target.client.exchange()
                call
                """).execute(target);

            CompletableFuture<CompletionStage<?>> started = new CompletableFuture<>();
            NonBlockingThread eventLoop = new NonBlockingThread(() -> {
                started.complete(PythonAsyncioRuntime.toCompletionStage(coroutine));
                // the event loop goes on with its work, completing the response
                response.complete("response");
            });
            eventLoop.start();

            // driving the coroutine on the event loop thread would wait for a response that thread never sends
            CompletionStage<?> stage = started.get(10, TimeUnit.SECONDS);
            String[] result = stage.toCompletableFuture().get(10, TimeUnit.SECONDS).toString().split("\\|");
            assertNotEquals(eventLoop.getName(), result[0]);
            assertEquals("response", result[1]);
            eventLoop.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    public static final class DeferredClient {
        private final CompletableFuture<String> response;

        DeferredClient(CompletableFuture<String> response) {
            this.response = response;
        }

        public CompletionStage<String> exchange() {
            return response;
        }
    }

    private static final class NonBlockingThread extends Thread implements NonBlocking {
        NonBlockingThread(Runnable runnable) {
            super(runnable, "test-event-loop");
        }
    }
}
