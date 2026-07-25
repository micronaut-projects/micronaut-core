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
/**
 * JPMS module descriptor.
 */
module io.micronaut.http.netty {
    requires transitive io.micronaut.http;
    requires transitive io.micronaut.buffer.netty;

    requires transitive io.netty.codec.http;
    requires transitive io.netty.codec.http2;
    requires transitive io.netty.common;
    requires transitive io.netty.transport;
    requires transitive io.netty.handler;

    requires reactor.core;

    requires static org.graalvm.nativeimage;
    requires static io.micronaut.websocket;

    exports io.micronaut.http.netty;
    exports io.micronaut.http.netty.body;
    exports io.micronaut.http.netty.channel;
    exports io.micronaut.http.netty.channel.converters;
    exports io.micronaut.http.netty.channel.loom;
    exports io.micronaut.http.netty.configuration;
    exports io.micronaut.http.netty.content;
    exports io.micronaut.http.netty.cookies;
    exports io.micronaut.http.netty.reactive;
    exports io.micronaut.http.netty.stream;
    exports io.micronaut.http.netty.websocket;
}
