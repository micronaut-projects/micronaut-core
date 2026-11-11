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
package io.micronaut.http.server.netty.websocket;

import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The completion of a WebSocket handler, which may release the upgrade request, is counted on
 * the event loop of the channel exactly once, wherever the handler completed.
 */
class WebSocketHandlerCompletionTest {

    private final EventLoop eventLoop = new DefaultEventLoop();

    @AfterEach
    void shutDown() throws InterruptedException {
        eventLoop.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
    }

    @Test
    void completionOnTheEventLoopIsCountedInline() throws Exception {
        List<Thread> ranOn = new CopyOnWriteArrayList<>();
        CompletableFuture<Integer> ranBeforeReturn = new CompletableFuture<>();
        eventLoop.execute(() -> {
            NettyServerWebSocketHandler.runOnEventLoop(eventLoop, () -> ranOn.add(Thread.currentThread()));
            ranBeforeReturn.complete(ranOn.size());
        });

        assertEquals(1, ranBeforeReturn.get(10, TimeUnit.SECONDS));
        assertEquals(1, ranOn.size());
        assertTrue(eventLoop.inEventLoop(ranOn.getFirst()));
    }

    @Test
    void completionOffTheEventLoopIsCountedOnTheEventLoop() throws Exception {
        List<Thread> ranOn = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> ran = new CompletableFuture<>();
        NettyServerWebSocketHandler.runOnEventLoop(eventLoop, () -> {
            ranOn.add(Thread.currentThread());
            ran.complete(null);
        });

        ran.get(10, TimeUnit.SECONDS);
        // the event loop runs its tasks in order: once this one ran, the task ran just once
        eventLoop.submit(() -> { }).sync();
        assertEquals(1, ranOn.size());
        assertTrue(eventLoop.inEventLoop(ranOn.getFirst()));
    }

    @Test
    void completionAfterTheEventLoopShutDownIsCountedInline() throws Exception {
        eventLoop.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();

        List<Thread> ranOn = new CopyOnWriteArrayList<>();
        NettyServerWebSocketHandler.runOnEventLoop(eventLoop, () -> ranOn.add(Thread.currentThread()));

        // the rejected task still counts the completion, so that the request is released
        assertEquals(List.of(Thread.currentThread()), ranOn);
    }
}
