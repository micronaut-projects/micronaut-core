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
module io.micronaut.http {
    requires transitive io.micronaut.context;
    requires transitive io.micronaut.core.reactive;
    requires transitive io.micronaut.context.propagation;

    requires reactor.core;

    requires static kotlin.stdlib;
    requires static kotlinx.coroutines.core;
    requires static kotlinx.coroutines.reactor;

    requires static com.fasterxml.jackson.annotation;
    requires static org.jspecify;

    exports io.micronaut.http;
    exports io.micronaut.http.annotation;
    exports io.micronaut.http.bind;
    exports io.micronaut.http.bind.binders;
    exports io.micronaut.http.body;
    exports io.micronaut.http.body.stream;
    exports io.micronaut.http.cachecontrol;
    exports io.micronaut.http.codec;
    exports io.micronaut.http.context;
    exports io.micronaut.http.context.event;
    exports io.micronaut.http.converters;
    exports io.micronaut.http.cookie;
    exports io.micronaut.http.exceptions;
    exports io.micronaut.http.expression;
    exports io.micronaut.http.filter;
    exports io.micronaut.http.form;
    exports io.micronaut.http.hateoas;
    exports io.micronaut.http.multipart;
    exports io.micronaut.http.reactive.execution;
    exports io.micronaut.http.resource;
    exports io.micronaut.http.simple;
    exports io.micronaut.http.simple.cookies;
    exports io.micronaut.http.sse;
    exports io.micronaut.http.ssl;
    exports io.micronaut.http.uri;
    exports io.micronaut.http.util;
    exports io.micronaut.runtime.http.codec;
    exports io.micronaut.runtime.http.scope;
}
