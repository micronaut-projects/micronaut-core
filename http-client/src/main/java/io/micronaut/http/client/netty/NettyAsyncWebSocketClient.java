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
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.netty.websocket.NettyWebSocketClientHandler;
import io.micronaut.websocket.AsyncWebSocketClient;
import org.jspecify.annotations.Nullable;

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
        // the connect completes on the event loop: the continuations of the stage run in the
        // context of the caller, as the subscriber of the reactive connect does
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        ConnectFuture<T> future = new ConnectFuture<>();
        flow.onComplete((handler, error) -> {
            future.completedByConnect = true;
            boolean completed;
            if (propagatedContext.isEmpty()) {
                completed = complete(future, handler, error);
            } else {
                completed = propagatedContext.propagate(() -> complete(future, handler, error));
            }
            if (!completed && handler != null) {
                // cancelled, or completed otherwise, while the handshake completed: nobody waits
                // for this endpoint. Close the session itself: the close method of a concrete
                // endpoint class may not
                handler.closeUnclaimed();
            }
        });
        // cancelling the future, or completing it otherwise, e.g. with orTimeout, aborts the connect
        future.whenComplete((ignored, error) -> {
            if (!future.completedByConnect) {
                flow.cancel();
            }
        });
        return future;
    }

    private static <T extends AutoCloseable> boolean complete(CompletableFuture<T> future,
                                                              @Nullable NettyWebSocketClientHandler<T> handler,
                                                              @Nullable Throwable error) {
        if (error != null) {
            return future.completeExceptionally(error);
        }
        return handler != null && future.complete(handler.getClientEndpoint());
    }

    /**
     * The future of a connect.
     *
     * @param <T> The endpoint type
     */
    private static final class ConnectFuture<T> extends CompletableFuture<T> {
        /**
         * Set before the connect completes this future: a completion before that, e.g. a cancel or
         * a timeout, aborts the connect.
         */
        volatile boolean completedByConnect;
    }
}
