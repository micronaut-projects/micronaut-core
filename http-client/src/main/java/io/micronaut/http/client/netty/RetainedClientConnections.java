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
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Idle connections that outlive the HTTP client that opened them. As a client shuts down, it offers its idle HTTP/1.1
 * connections, with no request on them, after taking every handler of its own off their pipelines: what is kept is
 * the transport, and the TLS session of a secure connection. The next client of the same configuration takes them
 * back, adds its own handlers, and pools them as connections it opened itself.
 *
 * <p>Development mode provides it, so that the connections of the HTTP clients, their TCP and TLS setup, are kept while
 * one generation of the application stops and the next one starts. The client objects, their codecs, filters and
 * pipelines' handlers are each generation's own. Without it a client looks for nothing and closes its connections as
 * it shuts down.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public interface RetainedClientConnections {

    /**
     * Whether the connections of a client made of these may outlive it: none of them may be of the classes of a
     * context that does not outlive the client, such as the application's own.
     *
     * @param components The configuration, the TLS builders and the channel factory of a client
     * @return True if its connections may be offered
     */
    boolean isRetainable(List<?> components);

    /**
     * Offers an idle connection of a client that shuts down, called on the connection's event loop. The pipeline holds
     * the transport's handlers only, and the TLS handler of a secure connection.
     *
     * @param client The identity of the client's configuration, shared by the clients that may take it back
     * @param remote The remote it is connected to: its host, port and whether it is secure, equal for an equal remote
     * @param channel The connection
     * @return True if it is kept, false for the client to close it
     */
    boolean retain(String client, Object remote, Channel channel);

    /**
     * The connections kept for clients of a configuration, by the remote they are connected to, and the event loop
     * each runs on.
     *
     * @param client The identity of the client's configuration
     * @return The remotes and event loops of the connections kept
     */
    List<Map.Entry<Object, EventLoop>> retained(String client);

    /**
     * Takes back a connection kept for clients of a configuration, to a remote, on an event loop.
     *
     * @param client The identity of the client's configuration
     * @param remote The remote
     * @param eventLoop The event loop the connection must run on
     * @return The connection, which is no longer kept, or {@code null}
     */
    @Nullable Channel adopt(String client, Object remote, EventLoop eventLoop);
}
