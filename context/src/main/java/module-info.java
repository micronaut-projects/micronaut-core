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
module io.micronaut.context {
    requires transitive io.micronaut.aop;
    requires transitive io.micronaut.inject;

    requires static ch.qos.logback.classic;
    requires static ch.qos.logback.core;
    requires static org.apache.logging.log4j;
    requires static org.apache.logging.log4j.core;

    requires static jakarta.validation;

    exports io.micronaut.context.time;
    exports io.micronaut.logging;
    exports io.micronaut.logging.impl;
    exports io.micronaut.runtime;
    exports io.micronaut.runtime.beans;
    exports io.micronaut.runtime.context;
    exports io.micronaut.runtime.context.env;
    exports io.micronaut.runtime.context.scope;
    exports io.micronaut.runtime.context.scope.refresh;
    exports io.micronaut.runtime.converters.time;
    exports io.micronaut.runtime.event;
    exports io.micronaut.runtime.event.annotation;
    exports io.micronaut.runtime.exceptions;
    exports io.micronaut.runtime.graceful;
    exports io.micronaut.runtime.server;
    exports io.micronaut.runtime.server.event;
    exports io.micronaut.runtime.server.watch.event;
    exports io.micronaut.scheduling;
    exports io.micronaut.scheduling.annotation;
    exports io.micronaut.scheduling.async;
    exports io.micronaut.scheduling.cron;
    exports io.micronaut.scheduling.exceptions;
    exports io.micronaut.scheduling.executor;
    exports io.micronaut.scheduling.instrument;
    exports io.micronaut.scheduling.io.watch;
    exports io.micronaut.scheduling.io.watch.event;
    exports io.micronaut.scheduling.processor;
}
