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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.python.PythonAsyncioConfiguration;
import io.micronaut.context.python.PythonEventLoop;
import io.micronaut.context.python.PythonEventLoopProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import io.netty.channel.EventLoop;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Netty-backed Python event-loop provider.
 */
@Internal
@Singleton
@Requires(property = PythonAsyncioConfiguration.ENABLED, notEquals = "false")
public final class NettyPythonEventLoopProvider implements PythonEventLoopProvider, GracefulShutdownCapable {
    private static final ThreadLocal<NettyPythonEventLoop> CURRENT = new ThreadLocal<>();
    private static final NettyPythonEventLoopSupport SUPPORT = new NettyPythonEventLoopSupport();

    @Override
    public Optional<PythonEventLoop> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    @Override
    public CompletionStage<?> shutdownGracefully() {
        return SUPPORT.closeAll();
    }

    /**
     * Bind a Netty event loop as current for the calling thread.
     *
     * @param eventLoop The Netty event loop.
     * @return A scope that restores the previous current event loop.
     */
    public static Scope bind(EventLoop eventLoop) {
        NettyPythonEventLoop previous = CURRENT.get();
        CURRENT.set(new NettyPythonEventLoop(eventLoop, SUPPORT));
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    /**
     * Scope for a bound Netty event loop.
     */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
