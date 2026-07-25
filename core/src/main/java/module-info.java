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
module io.micronaut.core {
    uses io.micronaut.core.optim.StaticOptimizations.Loader;

    requires transitive org.slf4j;
    requires transitive org.jspecify;

    requires static jakarta.annotation;
    requires static org.graalvm.nativeimage;
    requires static io.netty.common;
    requires static kotlin.stdlib;
    requires static org.jetbrains.annotations;

    exports io.micronaut.core.annotation;
    exports io.micronaut.core.attr;
    exports io.micronaut.core.beans;
    exports io.micronaut.core.beans.exceptions;
    exports io.micronaut.core.bind;
    exports io.micronaut.core.bind.annotation;
    exports io.micronaut.core.bind.exceptions;
    exports io.micronaut.core.cli;
    exports io.micronaut.core.cli.exceptions;
    exports io.micronaut.core.convert;
    exports io.micronaut.core.convert.converters;
    exports io.micronaut.core.convert.exceptions;
    exports io.micronaut.core.convert.format;
    exports io.micronaut.core.convert.value;
    exports io.micronaut.core.exceptions;
    exports io.micronaut.core.execution;
    exports io.micronaut.core.expressions;
    exports io.micronaut.core.graal;
    exports io.micronaut.core.io;
    exports io.micronaut.core.io.buffer;
    exports io.micronaut.core.io.file;
    exports io.micronaut.core.io.scan;
    exports io.micronaut.core.io.service;
    exports io.micronaut.core.io.socket;
    exports io.micronaut.core.io.value;
    exports io.micronaut.core.naming;
    exports io.micronaut.core.naming.conventions;
    exports io.micronaut.core.optim;
    exports io.micronaut.core.order;
    exports io.micronaut.core.propagation;
    exports io.micronaut.core.reflect;
    exports io.micronaut.core.reflect.exception;
    exports io.micronaut.core.serialize;
    exports io.micronaut.core.serialize.exceptions;
    exports io.micronaut.core.type;
    exports io.micronaut.core.util;
    exports io.micronaut.core.util.clhm;
    exports io.micronaut.core.util.functional;
    exports io.micronaut.core.util.locale;
    exports io.micronaut.core.value;
    exports io.micronaut.core.version;
    exports io.micronaut.core.version.annotation;
}
