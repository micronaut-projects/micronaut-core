/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.netty.websocket;

import io.netty.channel.group.ChannelGroup;
import io.netty.channel.Channel;

/**
 * Defines a ChannelGroup repository to handle WebSockets.
 *
 * @author sdelamo
 * @since 1.0
 */
public interface WebSocketSessionRepository {

    /**
     * Adds a channel to the repository.
     * @param channel The channel
     */
    void addChannel(Channel channel);

    /**
     * Remove a channel from the repository.
     * @param channel The channel
     */
    void removeChannel(Channel channel);

    /**
     * Returns the {@link io.netty.channel.group.ChannelGroup} used to store WebSocketSessions.
     * @return A {@link io.netty.channel.group.ChannelGroup}
     */
    ChannelGroup getChannelGroup();
}
