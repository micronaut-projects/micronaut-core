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
module io.micronaut.retry {
    requires transitive io.micronaut.context;
    requires transitive io.micronaut.core.reactive;

    requires reactor.core;

    requires static jakarta.validation;

    exports io.micronaut.retry;
    exports io.micronaut.retry.annotation;
    exports io.micronaut.retry.event;
    exports io.micronaut.retry.exception;
    exports io.micronaut.retry.intercept;
}
