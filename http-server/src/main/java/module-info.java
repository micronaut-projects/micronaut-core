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
module io.micronaut.http.server {
    requires transitive io.micronaut.http;
    requires transitive io.micronaut.router;

    requires reactor.core;

    requires static io.micronaut.websocket;
    requires static io.micronaut.jackson.databind;

    requires static kotlinx.coroutines.core;
    requires static kotlinx.coroutines.reactor;
    requires static org.apache.groovy;

    exports io.micronaut.http.server;
    exports io.micronaut.http.server.annotation;
    exports io.micronaut.http.server.binding;
    exports io.micronaut.http.server.body;
    exports io.micronaut.http.server.codec;
    exports io.micronaut.http.server.cors;
    exports io.micronaut.http.server.exceptions;
    exports io.micronaut.http.server.exceptions.response;
    exports io.micronaut.http.server.filter;
    exports io.micronaut.http.server.multipart;
    exports io.micronaut.http.server.types;
    exports io.micronaut.http.server.types.files;
    exports io.micronaut.http.server.util;
    exports io.micronaut.http.server.util.locale;
    exports io.micronaut.http.server.websocket;
}
