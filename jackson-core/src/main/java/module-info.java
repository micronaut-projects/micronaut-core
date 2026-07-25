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
module io.micronaut.jackson {
    requires transitive io.micronaut.json;

    requires transitive tools.jackson.core;
    requires transitive com.fasterxml.jackson.annotation;

    requires static io.netty.buffer;

    exports io.micronaut.jackson.core.env;
    exports io.micronaut.jackson.core.parser;
    exports io.micronaut.jackson.core.tree;
}
