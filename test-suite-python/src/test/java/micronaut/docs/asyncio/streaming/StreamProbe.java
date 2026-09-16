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
package micronaut.docs.asyncio.streaming;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lifecycle counters the Python streaming fixtures report into, so tests can prove that a generator
 * started, that it was closed on disconnect, and that it was waiting when the client already had data.
 */
public final class StreamProbe {
    private static final ConcurrentHashMap<String, AtomicInteger> STARTED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> FINISHED = new ConcurrentHashMap<>();
    private static volatile boolean awaitingGate;

    private StreamProbe() {
    }

    public static void reset() {
        STARTED.clear();
        FINISHED.clear();
        awaitingGate = false;
    }

    public static void started(String name) {
        STARTED.computeIfAbsent(name, ignored -> new AtomicInteger()).incrementAndGet();
    }

    public static void finished(String name) {
        FINISHED.computeIfAbsent(name, ignored -> new AtomicInteger()).incrementAndGet();
    }

    public static int started(String name, int ignored) {
        AtomicInteger count = STARTED.get(name);
        return count == null ? 0 : count.get();
    }

    public static int finished(String name, int ignored) {
        AtomicInteger count = FINISHED.get(name);
        return count == null ? 0 : count.get();
    }

    public static void awaitingGate(boolean awaiting) {
        awaitingGate = awaiting;
    }

    public static boolean isAwaitingGate() {
        return awaitingGate;
    }

    public static boolean awaitGate(int seconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (!awaitingGate) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }

    /**
     * Open a raw HTTP/1.1 connection, read at least {@code minBytes} of the response and close the
     * socket: a client disconnect the server sees as a closed channel. The Micronaut client drains an
     * abandoned streaming body to reuse the connection instead, which never ends for an endless stream.
     *
     * @param port The server port
     * @param path The request path
     * @param minBytes How much of the response to read before disconnecting
     * @return The response bytes read, as text
     */
    public static String readAndDisconnect(int port, String path, int minBytes) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nAccept: application/x-json-stream\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            StringBuilder received = new StringBuilder();
            byte[] buffer = new byte[1024];
            while (received.length() < minBytes) {
                int read = in.read(buffer);
                if (read < 0) {
                    break;
                }
                received.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
            return received.toString();
        }
    }

    /**
     * Consume a publisher to completion on the calling thread.
     *
     * @param publisher The publisher
     * @return The failure's class name, or {@code null} when the stream completed
     */
    @SuppressWarnings("unchecked")
    public static String blockingFailure(Publisher<?> publisher) throws InterruptedException {
        CompletableFuture<Void> done = new CompletableFuture<>();
        ((Publisher<Object>) publisher).subscribe(new Subscriber<Object>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Object item) {
            }

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        try {
            done.get(30, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause().getClass().getName();
        } catch (java.util.concurrent.TimeoutException e) {
            return "timeout";
        }
    }

    /**
     * Wait until the named generator finished the expected number of times.
     *
     * @param name The generator name
     * @param expected The expected number of finishes
     * @param seconds The timeout
     * @return Whether the count was reached
     */
    public static boolean awaitFinished(String name, int expected, int seconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (finished(name, 0) < expected) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }
}
