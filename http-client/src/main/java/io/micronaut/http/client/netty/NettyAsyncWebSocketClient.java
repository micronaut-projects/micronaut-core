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
        return toStage(client.connectFlow(clientEndpointType, request));
    }

    @Override
    public <T extends AutoCloseable> CompletionStage<T> connect(Class<T> clientEndpointType, Map<String, Object> parameters) {
        return toStage(client.connectFlow(clientEndpointType, parameters));
    }

    @Override
    public void close() {
        client.close();
    }

    private static <T extends AutoCloseable> CompletionStage<T> toStage(ExecutionFlow<T> flow) {
        ConnectFuture<T> future = new ConnectFuture<>(flow);
        flow.onComplete((endpoint, error) -> {
            if (error != null) {
                future.completeExceptionally(error);
            } else if (endpoint != null && !future.complete(endpoint)) {
                // cancelled while the handshake completed: nobody waits for this endpoint
                closeQuietly(endpoint);
            }
        });
        return future;
    }

    private static void closeQuietly(AutoCloseable endpoint) {
        try {
            endpoint.close();
        } catch (Exception e) {
            // the connect was cancelled
        }
    }

    /**
     * The future of a connect: cancelling it cancels the connect.
     *
     * @param <T> The endpoint type
     */
    private static final class ConnectFuture<T> extends CompletableFuture<T> {
        private final ExecutionFlow<T> flow;

        ConnectFuture(ExecutionFlow<T> flow) {
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
