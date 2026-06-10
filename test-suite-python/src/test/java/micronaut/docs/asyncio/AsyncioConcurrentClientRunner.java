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
package micronaut.docs.asyncio;

import io.micronaut.http.client.HttpClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class AsyncioConcurrentClientRunner {
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private static final AtomicInteger MAX_ACTIVE = new AtomicInteger();

    private AsyncioConcurrentClientRunner() {
    }

    public static void resetActive() {
        ACTIVE.set(0);
        MAX_ACTIVE.set(0);
    }

    public static void enterActive() {
        int active = ACTIVE.incrementAndGet();
        MAX_ACTIVE.accumulateAndGet(active, Math::max);
    }

    public static void exitActive() {
        ACTIVE.decrementAndGet();
    }

    public static int maxActive() {
        return MAX_ACTIVE.get();
    }

    public static long retrieveConcurrently(HttpClient client, String path, String expected, int requestCount) throws Exception {
        String warmup = client.toBlocking().retrieve(path);
        if (!expected.equals(warmup)) {
            throw new AssertionError("Unexpected async warmup response: " + warmup);
        }
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        long started = System.nanoTime();
        try {
            List<Callable<String>> requests = new ArrayList<>(requestCount);
            for (int i = 0; i < requestCount; i++) {
                requests.add(() -> client.toBlocking().retrieve(path));
            }
            List<Future<String>> futures = executor.invokeAll(requests);
            for (Future<String> future : futures) {
                String result = get(future);
                if (!expected.equals(result)) {
                    throw new AssertionError("Unexpected async response: " + result);
                }
            }
        } finally {
            executor.shutdownNow();
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static String get(Future<String> future) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(5, TimeUnit.SECONDS);
    }
}
