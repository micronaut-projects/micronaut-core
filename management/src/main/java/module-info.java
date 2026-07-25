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
module io.micronaut.management {
    requires transitive io.micronaut.router;
    requires transitive io.micronaut.discovery;

    requires reactor.core;

    requires static io.micronaut.http.client;
    requires static io.micronaut.jackson.databind;

    requires static jakarta.validation;
    requires static java.sql;

    requires static ch.qos.logback.classic;
    requires static ch.qos.logback.core;
    requires static org.apache.logging.log4j;
    requires static org.apache.logging.log4j.core;

    exports io.micronaut.management.endpoint;
    exports io.micronaut.management.endpoint.annotation;
    exports io.micronaut.management.endpoint.beans;
    exports io.micronaut.management.endpoint.beans.impl;
    exports io.micronaut.management.endpoint.env;
    exports io.micronaut.management.endpoint.health;
    exports io.micronaut.management.endpoint.health.filter;
    exports io.micronaut.management.endpoint.info;
    exports io.micronaut.management.endpoint.info.impl;
    exports io.micronaut.management.endpoint.info.source;
    exports io.micronaut.management.endpoint.loggers;
    exports io.micronaut.management.endpoint.loggers.impl;
    exports io.micronaut.management.endpoint.processors;
    exports io.micronaut.management.endpoint.refresh;
    exports io.micronaut.management.endpoint.routes;
    exports io.micronaut.management.endpoint.routes.impl;
    exports io.micronaut.management.endpoint.stop;
    exports io.micronaut.management.endpoint.threads;
    exports io.micronaut.management.endpoint.threads.impl;
    exports io.micronaut.management.health.aggregator;
    exports io.micronaut.management.health.indicator;
    exports io.micronaut.management.health.indicator.annotation;
    exports io.micronaut.management.health.indicator.client;
    exports io.micronaut.management.health.indicator.discovery;
    exports io.micronaut.management.health.indicator.diskspace;
    exports io.micronaut.management.health.indicator.jdbc;
    exports io.micronaut.management.health.indicator.service;
    exports io.micronaut.management.health.indicator.threads;
    exports io.micronaut.management.health.monitor;
}
