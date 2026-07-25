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
module io.micronaut.aop {
    requires transitive io.micronaut.inject;
    requires transitive io.micronaut.core;

    requires static io.micronaut.core.reactive;
    requires static org.reactivestreams;
    requires static reactor.core;

    requires static kotlin.stdlib;

    exports io.micronaut.aop;
    exports io.micronaut.aop.chain;
    exports io.micronaut.aop.exceptions;
    exports io.micronaut.aop.internal;
    exports io.micronaut.aop.internal.intercepted;
    exports io.micronaut.aop.kotlin;
    exports io.micronaut.aop.runtime;
    exports io.micronaut.aop.util;
}
