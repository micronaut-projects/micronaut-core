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
module io.micronaut.jackson.databind {
    requires transitive io.micronaut.jackson;
    requires transitive tools.jackson.databind;

    requires static org.graalvm.nativeimage;
    requires static jakarta.validation;

    exports io.micronaut.jackson;
    exports io.micronaut.jackson.annotation;
    exports io.micronaut.jackson.codec;
    exports io.micronaut.jackson.databind;
    exports io.micronaut.jackson.databind.convert;
    exports io.micronaut.jackson.env;
    exports io.micronaut.jackson.serialize;
    exports io.micronaut.jackson.validation;
}
