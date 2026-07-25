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
module io.micronaut.inject {
    requires transitive org.slf4j;
    requires transitive io.micronaut.core;
    requires transitive jakarta.inject;
    requires transitive jakarta.annotation;

    requires static org.yaml.snakeyaml;
    requires static kotlin.stdlib;
    requires static org.apache.groovy;
    requires static org.jetbrains.annotations;

    exports io.micronaut.context;
    exports io.micronaut.context.annotation;
    exports io.micronaut.context.banner;
    exports io.micronaut.context.beans;
    exports io.micronaut.context.bind;
    exports io.micronaut.context.condition;
    exports io.micronaut.context.conditions;
    exports io.micronaut.context.converters;
    exports io.micronaut.context.env;
    exports io.micronaut.context.env.exp;
    exports io.micronaut.context.env.yaml;
    exports io.micronaut.context.event;
    exports io.micronaut.context.exceptions;
    exports io.micronaut.context.expressions;
    exports io.micronaut.context.i18n;
    exports io.micronaut.context.processor;
    exports io.micronaut.context.scope;
    exports io.micronaut.inject;
    exports io.micronaut.inject.annotation;
    exports io.micronaut.inject.beans;
    exports io.micronaut.inject.provider;
    exports io.micronaut.inject.proxy;
    exports io.micronaut.inject.qualifiers;
    exports io.micronaut.inject.validation;
}
