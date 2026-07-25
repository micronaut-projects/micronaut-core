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
module io.micronaut.http.client {
    requires transitive io.micronaut.http;
    requires transitive io.micronaut.json;
    requires transitive io.micronaut.discovery;

    requires reactor.core;

    requires static kotlin.stdlib;

    exports io.micronaut.http.client;
    exports io.micronaut.http.client.annotation;
    exports io.micronaut.http.client.bind;
    exports io.micronaut.http.client.bind.binders;
    exports io.micronaut.http.client.exceptions;
    exports io.micronaut.http.client.filter;
    exports io.micronaut.http.client.interceptor;
    exports io.micronaut.http.client.interceptor.configuration;
    exports io.micronaut.http.client.loadbalance;
    exports io.micronaut.http.client.multipart;
    exports io.micronaut.http.client.sse;
}
