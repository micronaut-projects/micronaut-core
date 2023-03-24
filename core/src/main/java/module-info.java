/*
 * Copyright 2017-2023 original authors
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
 * The core of micronaut provides the core functionality that most modules share
 * primarily consisting of utility classes, annotation, exceptions and interfaces
 */
module micronaut.core {
    requires org.slf4j;
    requires kotlin.stdlib;
    requires jakarta.annotation;
    requires org.graalvm.sdk;

    exports io.micronaut.core.attr;
    exports io.micronaut.core.annotation;
    exports io.micronaut.core.exceptions;
    exports io.micronaut.core.order;
    exports io.micronaut.core.type;//all implementations are package-private and @Internal
    exports io.micronaut.core.value;
    exports io.micronaut.core.version;

    //has internal classes (eg AbstractBeanIntrospection) which cannot be moved to an internal package and have to be accessible
    exports io.micronaut.core.beans;
    exports io.micronaut.core.beans.exceptions;

    exports io.micronaut.core.bind;
    exports io.micronaut.core.bind.annotation;
    exports io.micronaut.core.bind.exceptions;

    exports io.micronaut.core.cli.exceptions;
    exports io.micronaut.core.cli;//still contains internal package-private classes

    exports io.micronaut.core.convert;//still contains internal package-private classes
    exports io.micronaut.core.convert.value;
    exports io.micronaut.core.convert.format;
    exports io.micronaut.core.convert.exceptions;
    exports io.micronaut.core.convert.converters;


    exports io.micronaut.core.io;//still contains internal package-private classes
    exports io.micronaut.core.io.socket;//consider unexposing / internalizing
    exports io.micronaut.core.io.service;//contains primarily @internal package-private classes
    exports io.micronaut.core.io.scan;//still contains internal package-private classes
    exports io.micronaut.core.io.file;
    exports io.micronaut.core.io.buffer;

    exports io.micronaut.core.naming;
    exports io.micronaut.core.naming.conventions;

    opens io.micronaut.core.reflect;
    exports io.micronaut.core.reflect;//still contains internal package-private classes
    exports io.micronaut.core.reflect.exception;

    exports io.micronaut.core.serialize;
    exports io.micronaut.core.serialize.exceptions;

    exports io.micronaut.core.util;
    //exports io.micronaut.core.util.clhm; //classes not marked as internal but i suspect they should be
}
