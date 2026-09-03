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
package io.micronaut.context.python.netty;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.python.PythonAsyncioConfiguration;
import io.micronaut.context.python.PythonEventLoop;
import io.micronaut.context.python.PythonEventLoopProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import io.netty.channel.EventLoop;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.lang.ScopedValue.CallableOp;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Netty-backed Python event-loop provider.
 */
@Internal
@Singleton
@Requires(property = PythonAsyncioConfiguration.ENABLED_PROPERTY, notEquals = "false")
@Experimental
public final class NettyPythonEventLoopProvider implements PythonEventLoopProvider, GracefulShutdownCapable {
    private static final ScopedValue<NettyPythonEventLoop> CURRENT = ScopedValue.newInstance();
    private static final NettyPythonEventLoopSupport SUPPORT = new NettyPythonEventLoopSupport();

    @Override
    public Optional<PythonEventLoop> current() {
        return Optional.ofNullable(currentLoop());
    }

    @Override
    public @Nullable NettyPythonEventLoop currentLoop() {
        return CURRENT.isBound() ? CURRENT.get() : null;
    }

    @Override
    public CompletionStage<?> shutdownGracefully() {
        return SUPPORT.closeAll();
    }

    /**
     * Run an operation with a Netty event loop bound as current.
     *
     * @param eventLoop The Netty event loop.
     * @param operation The operation to run.
     */
    public static void bind(EventLoop eventLoop, Runnable operation) {
        ScopedValue.where(CURRENT, new NettyPythonEventLoop(eventLoop, SUPPORT)).run(operation);
    }

    /**
     * Call an operation with a Netty event loop bound as current.
     *
     * @param eventLoop The Netty event loop.
     * @param operation The operation to call.
     * @param <T> The operation result type.
     * @param <X> The operation exception type.
     * @return The operation result.
     * @throws X If the operation fails.
     */
    public static <T, X extends Throwable> T bind(EventLoop eventLoop, CallableOp<T, X> operation) throws X {
        return ScopedValue.where(CURRENT, new NettyPythonEventLoop(eventLoop, SUPPORT)).call(operation);
    }
}
