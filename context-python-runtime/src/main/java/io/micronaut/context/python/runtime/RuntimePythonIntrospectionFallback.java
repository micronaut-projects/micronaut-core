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
package io.micronaut.context.python.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospectionFallback;

import java.util.Optional;

/** Generates introspections only for classes with an explicit compiler-written runtime model. */
@Internal
public final class RuntimePythonIntrospectionFallback implements BeanIntrospectionFallback {
    /** Creates a fallback for compiler-written Python runtime models. */
    public RuntimePythonIntrospectionFallback() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<BeanIntrospection<T>> findIntrospection(Class<T> beanType) {
        ClassLoader loader = beanType.getClassLoader();
        if (loader == null || loader.getResource(RuntimePythonModel.PATH + beanType.getName() + ".properties") == null) {
            return Optional.empty();
        }
        return Optional.of((BeanIntrospection<T>) RuntimePythonMetadata.introspection(beanType));
    }
}
