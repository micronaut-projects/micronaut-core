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

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import jakarta.annotation.PreDestroy;

/**
 * Tells the {@link DevClientConnections kept connections} when a generation stops: those it did not take back are
 * closed. Made by each generation, since the connections outlive the one that made this.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Context
@Requires(beans = DevClientConnections.class)
final class DevClientConnectionsWatch {

    private final DevClientConnections connections;

    DevClientConnectionsWatch(DevClientConnections connections) {
        this.connections = connections;
    }

    /**
     * The generation stops.
     */
    @PreDestroy
    void close() {
        connections.generationEnded();
    }
}
