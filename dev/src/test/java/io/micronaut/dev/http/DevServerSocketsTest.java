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
import java.net.ServerSocket;
import java.nio.channels.ServerSocketChannel;

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
}
