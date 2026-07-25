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
module io.micronaut.core.reactive {
    requires transitive io.micronaut.core;
    requires transitive org.reactivestreams;

    requires static reactor.core;
    requires static kotlinx.coroutines.core;

    exports io.micronaut.core.async;
    exports io.micronaut.core.async.annotation;
    exports io.micronaut.core.async.converters;
    exports io.micronaut.core.async.processor;
    exports io.micronaut.core.async.propagation;
    exports io.micronaut.core.async.publisher;
    exports io.micronaut.core.async.subscriber;

    provides io.micronaut.core.convert.TypeConverterRegistrar with
        io.micronaut.core.async.converters.ReactiveTypeConverterRegistrar;
    provides io.micronaut.core.type.TypeInformationProvider with
        io.micronaut.core.async.ReactiveStreamsTypeInformationProvider;
}
