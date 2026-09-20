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
package io.micronaut.python.processing.metadata;

import io.micronaut.core.annotation.Internal;

/**
 * Raised while a class is analysed for a model backend when the class needs a construct the model does not describe
 * but the compiler's writers do: interception, which needs a proxy class (an intercepted bean, an around or
 * introduction proxy, or a method adapted to an interface, such as an event listener), or a configuration builder,
 * which is wired from the configuration as the bean is injected. The bean definitions of such a class are written by
 * the compiler, whichever backend is selected, while its introspection is still described by its model.
 *
 * @since 5.3.0
 */
@Internal
final class PythonCompilerFallback extends RuntimeException {

    private final String construct;

    PythonCompilerFallback(String construct) {
        super("The class needs " + construct + ", which the compiler writes", null, false, false);
        this.construct = construct;
    }

    /**
     * @return What the class needs, named for the diagnostics
     */
    String construct() {
        return construct;
    }
}
