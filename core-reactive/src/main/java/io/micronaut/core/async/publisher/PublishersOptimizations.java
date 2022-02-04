/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.core.async.publisher;

import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * Static optimizations for the {@link Publishers class}.
 * This is used when we can compute the reactive types at
 * build time instead of runtime.
 *
 * @since 3.2.0
 */
@Internal
public final class PublishersOptimizations {

    private final List<Class<?>> reactiveTypes;
    private final List<Class<?>> singleTypes;
    private final List<Class<?>> completableTypes;

    public PublishersOptimizations(List<Class<?>> reactiveTypes, List<Class<?>> singleTypes, List<Class<?>> completableTypes) {
        this.reactiveTypes = reactiveTypes;
        this.singleTypes = singleTypes;
        this.completableTypes = completableTypes;
    }

    List<Class<?>> getReactiveTypes() {
        return reactiveTypes;
    }

    List<Class<?>> getSingleTypes() {
        return singleTypes;
    }

    List<Class<?>> getCompletableTypes() {
        return completableTypes;
    }
}
