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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.netty.websocket.NettyWebSocketClientHandler;
import io.micronaut.websocket.AsyncWebSocketClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The {@link AsyncWebSocketClient} of {@link NettyHttpClient}: the connect flows complete the
 * stages, without Reactor.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class NettyAsyncWebSocketClient implements AsyncWebSocketClient {

    private final NettyHttpClient client;

    /**
     * @param client The client
     */
    NettyAsyncWebSocketClient(NettyHttpClient client) {
        this.client = client;
    }

    @Override
    public <T extends AutoCloseable> CompletionStage<T> connect(Class<T> clientEndpointType, MutableHttpRequest<?> request) {
        ExecutionFlow<NettyWebSocketClientHandler<T>> flow;
        try {
            flow = client.connectFlow(clientEndpointType, request);
        } catch (RuntimeException e) {
            // a failed stage, like any other connect failure
            return CompletableFuture.failedFuture(e);
        }
        return toStage(flow);
    }

    @Override
    public <T extends AutoCloseable> CompletionStage<T> connect(Class<T> clientEndpointType, Map<String, Object> parameters) {
        ExecutionFlow<NettyWebSocketClientHandler<T>> flow;
        try {
            flow = client.connectFlow(clientEndpointType, parameters);
        } catch (RuntimeException e) {
            // for example a class that is not a client websocket: a failed stage, not an exception
            return CompletableFuture.failedFuture(e);
        }
        return toStage(flow);
    }

    @Override
    public void close() {
        client.close();
    }

    private static <T extends AutoCloseable> CompletionStage<T> toStage(ExecutionFlow<NettyWebSocketClientHandler<T>> flow) {
        ConnectFuture<T> future = new ConnectFuture<>(flow);
        flow.onComplete((handler, error) -> {
            if (error != null) {
                future.completeExceptionally(error);
            } else if (handler != null && !future.complete(handler.getClientEndpoint())) {
                // cancelled while the handshake completed: nobody waits for this endpoint. Close
                // the session itself: the close method of a concrete endpoint class may not
                handler.closeUnclaimed();
            }
        });
        return future;
    }

    /**
     * The future of a connect: cancelling it cancels the connect.
     *
     * @param <T> The endpoint type
     */
    private static final class ConnectFuture<T> extends CompletableFuture<T> {
        private final ExecutionFlow<?> flow;

        ConnectFuture(ExecutionFlow<?> flow) {
            this.flow = flow;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                flow.cancel();
            }
            return cancelled;
        }
    }
}
