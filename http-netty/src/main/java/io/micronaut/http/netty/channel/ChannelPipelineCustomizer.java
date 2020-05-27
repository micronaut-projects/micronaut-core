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
package io.micronaut.http.netty.channel;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Interface that allows customizations to the {@link io.netty.channel.ChannelPipeline}.
 *
 * @see ChannelPipelineListener
 * @author graemerocher
 * @since 2.0.0
 */
public interface ChannelPipelineCustomizer {
    String HANDLER_HTTP_COMPRESSOR = "http-compressor";
    String HANDLER_HTTP_DECOMPRESSOR = "http-decompressor";
    String HANDLER_HTTP_KEEP_ALIVE = "http-keep-alive-handler";
    String HANDLER_HTTP_AGGREGATOR = "http-aggregator";
    String HANDLER_HTTP_CHUNK = "chunk-writer";
    String HANDLER_HTTP_STREAM = "http-streams-codec";
    String HANDLER_HTTP_DECODER = "http-decoder";
    String HANDLER_HTTP_PROXY = "http-proxy";
    String HANDLER_HTTP_CLIENT_CODEC = "http-client-codec";
    String HANDLER_HTTP_SERVER_CODEC = "http-server-codec";
    String HANDLER_HTTP_CLIENT_INIT = "http-client-init";
    String HANDLER_FLOW_CONTROL = "flow-control-handler";
    String HANDLER_CONNECT_TTL = "connect-ttl";
    String HANDLER_IDLE_STATE = "idle-state";
    String HANDLER_MICRONAUT_WEBSOCKET_CLIENT = "micronaut-websocket-client";
    String HANDLER_SOCKS_5_PROXY = "socks5-proxy";
    String HANDLER_MICRONAUT_FULL_HTTP_RESPONSE = "micronaut-full-http-response";
    String HANDLER_READ_TIMEOUT = "read-timeout";
    String HANDLER_SSL = "ssl";
    String HANDLER_MICRONAUT_SSE_EVENT_STREAM = "micronaut-sse-event-stream";
    String HANDLER_MICRONAUT_SSE_CONTENT = "micronaut-sse-content";
    String HANDLER_MICRONAUT_HTTP_RESPONSE_STREAM = "micronaut-http-response-stream";
    String HANDLER_HTTP2_CONNECTION = "http2-connection";
    String HANDLER_HTTP2_SETTINGS = "http2-settings";
    String HANDLER_HTTP2_UPGRADE_REQUEST = "http2-upgrade-request";
    String HANDLER_HTTP2_PROTOCOL_NEGOTIATOR = "http2-protocol-negotiator";
    String HANDLER_WEBSOCKET_UPGRADE = "websocket-upgrade-handler";
    String HANDLER_MICRONAUT_INBOUND = "micronaut-inbound-handler";
    String HANDLER_ACCESS_LOGGER = "http-access-logger";

    /**
     * @return Is this customizer the client.
     */
    boolean isClientChannel();

    /**
     * @return Is this customizer the server.
     */
    default boolean isServerChannel() {
        return !isClientChannel();
    }

    /**
     * A hook to customize the pipeline upon establishing a connection.
     *
     * @param listener The listener The listener.
     */
    void doOnConnect(@NonNull ChannelPipelineListener listener);
}
