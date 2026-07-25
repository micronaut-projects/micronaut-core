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
module io.micronaut.http.server.netty {
    requires transitive io.micronaut.http.server;
    requires transitive io.micronaut.http.netty;

    requires transitive io.netty.codec.http;
    requires transitive io.netty.codec.compression;
    requires transitive io.netty.codec;
    requires transitive io.netty.transport;
    requires static reactor.core;

    requires static jdk.jfr;

    exports io.micronaut.http.server.netty;
    exports io.micronaut.http.server.netty.binders;
    exports io.micronaut.http.server.netty.configuration;
    exports io.micronaut.http.server.netty.handler;
    exports io.micronaut.http.server.netty.ssl;
    exports io.micronaut.http.server.netty.websocket;
}
