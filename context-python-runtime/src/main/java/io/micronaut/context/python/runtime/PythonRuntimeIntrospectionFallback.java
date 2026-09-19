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

/**
 * Known-class lookup of runtime-generated introspections, for introspectors that never went through a context and
 * therefore have no composed provider. A claimed class whose generation fails is an error, not a lookup miss.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonRuntimeIntrospectionFallback implements BeanIntrospectionFallback {

    /**
     * Creates the fallback.
     */
    public PythonRuntimeIntrospectionFallback() {
        PythonRuntimeIntrospectionsProvider.install();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<BeanIntrospection<T>> findIntrospection(Class<T> beanType) {
        return PythonRuntimeMetadata.findModel(beanType)
            .filter(model -> model.classModel().introspection() != null)
            .map(model -> (BeanIntrospection<T>) PythonRuntimeMetadata.introspection(beanType));
    }
}
