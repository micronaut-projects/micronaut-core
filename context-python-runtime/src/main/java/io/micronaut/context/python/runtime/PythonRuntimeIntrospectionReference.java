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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospectionReference;

/**
 * The lightweight reference of a runtime-generated introspection: discoverable and filterable from the saved model,
 * generated on first load.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonRuntimeIntrospectionReference implements BeanIntrospectionReference<Object> {

    private final Class<Object> beanType;

    @SuppressWarnings("unchecked")
    PythonRuntimeIntrospectionReference(Class<?> beanType) {
        this.beanType = (Class<Object>) beanType;
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public Class<Object> getBeanType() {
        return beanType;
    }

    @Override
    public BeanIntrospection<Object> load() {
        return PythonRuntimeMetadata.introspection(beanType);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return PythonRuntimeMetadata.annotationMetadata(beanType);
    }

    @Override
    public String getName() {
        return beanType.getName();
    }

    @Override
    public String toString() {
        return "PythonRuntimeIntrospectionReference(" + beanType.getName() + ")";
    }
}
