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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.DevRuntime;
import io.micronaut.http.server.netty.RetainedServerSockets;
import io.netty.channel.Channel;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;

/**
 * Hands the Netty server of each generation the listening sockets the development runtime keeps, so that
 * the port stays bound while one generation stops and the next starts.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(classes = RetainedServerSockets.class)
@Requires(condition = DevelopmentMode.Active.class)
@Requires(beans = DevRuntime.class)
final class DevRetainedServerSockets implements RetainedServerSockets {

    private final DevRuntime runtime;

    DevRetainedServerSockets(DevRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public @Nullable ServerSocketChannel serverSocket(@Nullable String host, int port) throws IOException {
        DevServerSockets sockets = runtime.serverSockets().orElse(null);
        return sockets == null ? null : sockets.serverSocket(host, port);
    }

    @Override
    public void accepting(ServerSocketChannel socket, Channel channel) {
        runtime.serverSockets().ifPresent(sockets -> sockets.accepting(autoRead -> channel.config().setAutoRead(autoRead), channel::isOpen));
    }
}
