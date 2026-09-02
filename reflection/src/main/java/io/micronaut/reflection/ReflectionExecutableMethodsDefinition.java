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
package io.micronaut.reflection;

import io.micronaut.context.AbstractExecutableMethodsDefinition;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * The executable methods of a {@link ReflectionBeanDefinition}, dispatched reflectively. As for a generated
 * bean, the metadata of an executable method is the metadata of the method under the metadata of the bean type.
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 5.2.0
 */
final class ReflectionExecutableMethodsDefinition<T> extends AbstractExecutableMethodsDefinition<T> {

    private static final String KOTLIN_CONTINUATION = "kotlin.coroutines.Continuation";

    private final Method[] methods;

    ReflectionExecutableMethodsDefinition(AnnotationMetadata typeMetadata, List<Method> methods) {
        super(references(typeMetadata, methods));
        this.methods = methods.toArray(Method[]::new);
        for (Method method : this.methods) {
            method.trySetAccessible();
        }
    }

    private static MethodReference[] references(AnnotationMetadata typeMetadata, List<Method> methods) {
        MethodReference[] references = new MethodReference[methods.size()];
        for (int i = 0; i < references.length; i++) {
            Method method = methods.get(i);
            Class<?>[] parameterTypes = method.getParameterTypes();
            references[i] = new MethodReference(
                method.getDeclaringClass(),
                metadataOf(typeMetadata, method),
                method.getName(),
                ReflectionArguments.returnOf(method),
                ReflectionArguments.argumentsOf(method),
                Modifier.isAbstract(method.getModifiers()),
                parameterTypes.length > 0 && KOTLIN_CONTINUATION.equals(parameterTypes[parameterTypes.length - 1].getName())
            );
        }
        return references;
    }

    /**
     * The metadata of an executable method of a bean: the method's under the bean type's, as the processors
     * generate it.
     */
    static AnnotationMetadata metadataOf(AnnotationMetadata typeMetadata, Method method) {
        AnnotationMetadata methodMetadata = ReflectionAnnotations.metadataOf(method);
        if (typeMetadata.isEmpty()) {
            return methodMetadata;
        }
        return new AnnotationMetadataHierarchy(typeMetadata, methodMetadata);
    }

    @Override
    protected Method getTargetMethodByIndex(int index) {
        return methods[index];
    }

    @Override
    @SuppressWarnings("NullAway") // a method can return null, the declaration of dispatch predates the nullness annotations
    protected Object dispatch(int index, T target, @Nullable Object[] args) {
        return ReflectionUtils.invokeMethod(target, methods[index], args);
    }
}
