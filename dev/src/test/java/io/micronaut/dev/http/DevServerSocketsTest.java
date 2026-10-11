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
package io.micronaut.dev.http;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevServerSocketsTest {

    @Test
    void aBatchTakenWhileAStartingServerHasNotClaimedItsSocketYetLeavesTheSocketOpen() throws IOException {
        try (DevServerSockets sockets = new DevServerSockets()) {
            // the first generation's server was handed the socket, and is about to register a channel over it
            ServerSocketChannel socket = sockets.serverSocket(null, freePort());
            assertNotNull(socket);

            // a change arrives before it does: the batch must not take the socket for a dropped listener's
            sockets.pause();
            assertTrue(socket.isOpen());

            // the server claims it, and keeps it across the next batch
            sockets.accepting(socket, autoRead -> { }, () -> true);
            sockets.pause();
            sockets.resume();
            assertTrue(socket.isOpen());
        }
    }

    @Test
    void aSocketNoServerClaimedOnceTheGenerationStartedIsReleased() throws IOException {
        try (DevServerSockets sockets = new DevServerSockets()) {
            int port = freePort();
            ServerSocketChannel socket = sockets.serverSocket(null, port);
            assertNotNull(socket);
            assertSame(socket, sockets.serverSocket(null, port));

            // the generation's servers all started and none accepts on it: its listener is gone
            sockets.releaseUnclaimed();
            assertFalse(socket.isOpen());
            assertFalse(sockets.isBound());
        }
    }

    @Test
    void aSocketHandedToANewerGenerationWhileTheLastOneWasAwaitedIsKept() throws IOException {
        try (DevServerSockets sockets = new DevServerSockets()) {
            int port = freePort();
            // the first generation's servers are awaited from this mark
            long mark = sockets.handoutMark();
            // a change restarts the application meanwhile, and the next generation's server is handed the socket
            ServerSocketChannel socket = sockets.serverSocket(null, port);
            assertNotNull(socket);

            // the wait for the first generation ends: what it releases is only what was handed out before it began
            sockets.releaseUnclaimed(mark);
            assertTrue(socket.isOpen());

            // the newer generation's own wait, from a mark taken after the handout, releases it if never claimed
            sockets.releaseUnclaimed(sockets.handoutMark());
            assertFalse(socket.isOpen());
        }
    }

    @Test
    void aGenerationThatIsNoLongerCurrentChangesNothing() throws IOException {
        try (DevServerSockets sockets = new DevServerSockets()) {
            ServerSocketChannel socket = sockets.serverSocket(null, freePort());
            assertNotNull(socket);

            // the first generation reports itself started after a restart replaced it: it touches no socket state
            assertFalse(sockets.stopServingUnavailable(() -> false));
            sockets.releaseUnclaimed(sockets.handoutMark(), () -> false);
            assertTrue(socket.isOpen());

            // the current generation does
            assertTrue(sockets.stopServingUnavailable(() -> true));
            sockets.releaseUnclaimed(sockets.handoutMark(), () -> true);
            assertFalse(socket.isOpen());
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    @Test
    void whileNoGenerationRunsAnAllowedHostIsToldGenericallyAndAnotherHostIsRefused() throws IOException {
        try (DevServerSockets sockets = new DevServerSockets()) {
            int port = freePort();
            assertNotNull(sockets.serverSocket(null, port));
            sockets.serveUnavailable();

            String unavailable = get(port, "localhost:" + port);
            assertTrue(unavailable.startsWith("HTTP/1.1 503 "), unavailable);
            assertTrue(unavailable.contains("\r\nRetry-After: 1\r\n"), unavailable);
            assertTrue(unavailable.endsWith(DevServerSockets.UNAVAILABLE_BODY), unavailable);
            assertTrue(get(port, "127.0.0.1:" + port).startsWith("HTTP/1.1 503 "));
            assertTrue(get(port, "[::1]:" + port).startsWith("HTTP/1.1 503 "));

            // a name made to resolve to this machine, and a request without a host
            String rebound = get(port, "rebind.attacker.example:" + port);
            assertTrue(rebound.startsWith("HTTP/1.1 403 "), rebound);
            assertTrue(get(port, null).startsWith("HTTP/1.1 403 "));
        }
    }

    @Test
    void theHostsAnsweredWhileNoGenerationRunsFollowTheAccessSettings() throws IOException {
        io.micronaut.dev.DevAccess access = new io.micronaut.dev.DevAccess(List.of("dev.example.test"), true);
        try (DevServerSockets sockets = new DevServerSockets(access::allowsHost)) {
            int port = freePort();
            assertNotNull(sockets.serverSocket(null, port));
            sockets.serveUnavailable();
            assertTrue(get(port, "dev.example.test:" + port).startsWith("HTTP/1.1 503 "));
            assertTrue(get(port, "other.example.test:" + port).startsWith("HTTP/1.1 403 "));
        }
    }

    @Test
    void onlyAFewConnectionsAreAnsweredAtOnceWhileNoGenerationRuns() throws Exception {
        try (DevServerSockets sockets = new DevServerSockets()) {
            int port = freePort();
            assertNotNull(sockets.serverSocket(null, port));
            sockets.serveUnavailable();

            // a peer opens many connections and sends nothing: each would hold a thread until the read times out
            try (IdleConnections idle = new IdleConnections()) {
                for (int i = 0; i < DevServerSockets.MAX_ANSWERING * 5; i++) {
                    idle.sockets.add(new Socket("localhost", port));
                }
                Thread.sleep(500);
                long answering = Thread.getAllStackTraces().keySet().stream()
                    .filter(thread -> thread.getName().equals(DevServerSockets.UNAVAILABLE_THREAD))
                    .count();
                assertTrue(answering <= DevServerSockets.MAX_ANSWERING, answering + " threads answer");
                assertTrue(answering > 0, "no thread answers");
            }

            // once those are gone, a request is answered again
            long deadline = System.nanoTime() + 10_000_000_000L;
            String answer = "";
            while (System.nanoTime() < deadline && !answer.startsWith("HTTP/1.1 503 ")) {
                answer = get(port, "localhost:" + port);
                if (!answer.startsWith("HTTP/1.1 503 ")) {
                    Thread.sleep(100);
                }
            }
            assertTrue(answer.startsWith("HTTP/1.1 503 "), answer);
        }
    }

    @Test
    void theHostOfARequestHeadIsRead() {
        assertEquals("localhost:8080", DevServerSockets.host("GET / HTTP/1.1\r\nAccept: */*\r\nhOsT:  localhost:8080 \r\n\r\n"));
        assertEquals(null, DevServerSockets.host("GET / HTTP/1.1\r\n\r\n"));
        // two hosts are none
        assertEquals(null, DevServerSockets.host("GET / HTTP/1.1\r\nHost: localhost\r\nHost: evil.example\r\n\r\n"));
    }

    private static String get(int port, String host) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET /hello HTTP/1.1\r\n" + (host == null ? "" : "Host: " + host + "\r\n") + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.net.SocketException e) {
            // closed without an answer: the responder is at capacity
            return "";
        }
    }

    /**
     * Connections a peer opened and sends nothing on, closed together.
     */
    private static final class IdleConnections implements AutoCloseable {
        final List<Socket> sockets = new ArrayList<>();

        @Override
        public void close() throws IOException {
            for (Socket socket : sockets) {
                socket.close();
            }
        }
    }
}
