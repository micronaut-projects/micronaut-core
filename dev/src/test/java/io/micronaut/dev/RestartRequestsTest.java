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
package io.micronaut.dev;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.http.DevServerSockets;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The request coordination of a restart, against generations whose servers are stand-ins: how long a restart waits for
 * a stopping generation's requests, what a failed start answers, and how a started generation's servers are awaited.
 */
class RestartRequestsTest {

    @TempDir
    Path project;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger runtimeLogger = (Logger) LoggerFactory.getLogger(DevRuntime.class);

    @BeforeEach
    void setUp() {
        logged.start();
        runtimeLogger.addAppender(logged);
    }

    @AfterEach
    void tearDown() {
        runtimeLogger.detachAppender(logged);
    }

    @Test
    void aRestartStopsWaitingForRequestsThatOutlastTheDrainTimeout() {
        RestartRequests requests = new RestartRequests(manifest("200ms"), false);
        StubServer stuck = new StubServer(true, CompletableFuture::new);
        try (ApplicationContext generation = ApplicationContext.builder().singletons(stuck).start()) {
            CompletableFuture<Void> admission = requests.batchStarted();
            assertFalse(requests.isAdmitted());

            long started = System.nanoTime();
            requests.beforeStop(generation, 3);
            long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            // the requests the batch held proceed on the stopping generation, which waits for them only so long
            assertTrue(admission.isDone());
            assertTrue(stuck.drained.get());
            assertTrue(waitedMillis >= 150 && waitedMillis < 10_000, "Waited " + waitedMillis + " ms");
            assertTrue(logged.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                && event.getFormattedMessage().contains("generation 3 after 200 ms")
                && event.getFormattedMessage().contains(DevManifest.REQUESTS_DRAIN_TIMEOUT)), "Logged: " + logged.list);
        }
    }

    @Test
    void aServerThatCannotDrainDoesNotHoldTheRestart() {
        RestartRequests requests = new RestartRequests(manifest("1m"), false);
        StubServer throwing = new StubServer(true, () -> {
            throw new IllegalStateException("cannot drain");
        });
        StubServer failing = new StubServer(true, () -> CompletableFuture.failedFuture(new IllegalStateException("drain failed")));
        StubServer stopped = new StubServer(false, CompletableFuture::new);
        try (ApplicationContext generation = ApplicationContext.builder().singletons(throwing, failing, stopped).start()) {
            long started = System.nanoTime();
            requests.beforeStop(generation, 2);

            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 10_000);
            assertTrue(throwing.drained.get());
            assertTrue(failing.drained.get());
            // a server that is not running has nothing to drain, and is not waited for
            assertFalse(stopped.drained.get());
            assertTrue(logged.list.stream().noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN)), "Logged: " + logged.list);
        }
    }

    @Test
    void withoutRetainedSocketsAStartedGenerationIsAwaitedUntilItsServerRuns() throws Exception {
        RestartRequests requests = new RestartRequests(manifest("1m"), false);
        assertTrue(requests.serverSockets().isEmpty());
        StubServer server = new StubServer(false, () -> CompletableFuture.completedFuture(null));
        try (ApplicationContext generation = ApplicationContext.builder().singletons(server).start()) {
            assertFalse(RestartRequests.serversRun(generation));
            Thread starter = new Thread(() -> {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                server.running.set(true);
            });
            starter.start();

            assertTrue(requests.started(generation, () -> true, () -> true));
            assertTrue(server.running.get());
            assertTrue(RestartRequests.serversRun(generation));
            starter.join();
        }
    }

    @Test
    void aFailedStartAnswersWithTheFailureUntilTheSocketsClose() throws IOException {
        RestartRequests requests = new RestartRequests(manifest("1m"), true);
        DevServerSockets sockets = requests.serverSockets().orElseThrow();
        int port = freePort();
        ServerSocketChannel socket = sockets.serverSocket(null, port);
        assertNotNull(socket);

        // a failure without a message is named by its type
        requests.startFailed(new IllegalStateException());
        String response = get(port);
        assertTrue(response.startsWith("HTTP/1.1 503 "), response);
        assertTrue(response.endsWith("The application is not running: java.lang.IllegalStateException\n"), response);

        requests.close();
        assertFalse(socket.isOpen());
        assertThrows(ConnectException.class, () -> get(port));
    }

    private DevManifest manifest(String drainTimeout) {
        Properties properties = new Properties();
        properties.setProperty("micronaut.dev.main-class", "app.Application");
        properties.setProperty("micronaut.dev.reloadable", "build/classes");
        properties.setProperty(DevManifest.REQUESTS_DRAIN_TIMEOUT, drainTimeout);
        return DevManifest.of(project, properties);
    }

    private static String get(int port) throws IOException {
        try (Socket client = new Socket()) {
            client.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
            client.setSoTimeout(5_000);
            client.getOutputStream().write("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            client.getInputStream().transferTo(response);
            return response.toString(StandardCharsets.UTF_8);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    /**
     * A server that runs or not, as told, and drains as given.
     */
    private static final class StubServer implements EmbeddedServer, GracefulShutdownCapable {
        final AtomicBoolean running;
        final AtomicBoolean drained = new AtomicBoolean();
        private final Supplier<CompletionStage<?>> drain;

        StubServer(boolean running, Supplier<CompletionStage<?>> drain) {
            this.running = new AtomicBoolean(running);
            this.drain = drain;
        }

        @Override
        public CompletionStage<?> shutdownGracefully() {
            drained.set(true);
            return drain.get();
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }

        @Override
        public int getPort() {
            return -1;
        }

        @Override
        public String getHost() {
            return "localhost";
        }

        @Override
        public String getScheme() {
            return "http";
        }

        @Override
        public URL getURL() {
            throw new UnsupportedOperationException();
        }

        @Override
        public URI getURI() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ApplicationContext getApplicationContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ApplicationConfiguration getApplicationConfiguration() {
            throw new UnsupportedOperationException();
        }
    }
}
