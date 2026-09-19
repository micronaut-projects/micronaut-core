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
import io.micronaut.core.type.Argument;
import io.micronaut.inject.beans.AbstractInitializableBeanIntrospection;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

/** Shared property metadata; the generated subclass implements direct constructor/getter/setter calls. */
@Internal
public abstract class RuntimePythonIntrospection extends AbstractInitializableBeanIntrospection<Object> {
    /**
     * Initializes shared introspection metadata.
     *
     * @param beanType The wrapper class described by this introspection
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected RuntimePythonIntrospection(Class<?> beanType) {
        super((Class<Object>) beanType, RuntimePythonModel.metadata(RuntimePythonModel.read(beanType).singleton()), AnnotationMetadata.EMPTY_METADATA, Argument.ZERO_ARGUMENTS, properties(beanType), new BeanMethodRef[0]);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BeanPropertyRef<Object>[] properties(Class<?> beanType) {
        var properties = RuntimePythonModel.read(beanType).properties();
        BeanPropertyRef<Object>[] refs = new BeanPropertyRef[properties.size()];
        for (int i = 0; i < refs.length; i++) {
            var property = properties.get(i);
            refs[i] = new BeanPropertyRef(Argument.of(property.javaType(), property.name()), i * 2, i * 2 + 1, -1, false, true);
        }
        return refs;
    }

    @Override
    protected final Object instantiateInternal(@Nullable Object @Nullable [] arguments) {
        if (arguments != null && arguments.length != 0) {
            throw new IllegalArgumentException("Runtime metadata prototype supports only a no-argument constructor");
        }
        return instantiate();
    }

    @Override
    protected final Method getTargetMethodByIndex(int index) {
        throw new UnsupportedOperationException("Runtime metadata prototype does not expose reflective method handles");
    }
}
