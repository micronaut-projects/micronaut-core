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
package io.micronaut.inject.reflection;

import io.micronaut.context.AnnotationReflectionUtils;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.AbstractBeanConstructor;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.ReflectionAnnotationMetadataBuilder;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The adapters of a specification API that names an executable by its {@link Method} or
 * {@link Constructor}: the generated metadata is used when it describes the named executable, and the
 * reflective metadata of this package otherwise — the one reflective step an API defined on
 * {@code java.lang.reflect} imposes.
 *
 * @author Denis Stepanov
 * @since 5.1
 */
@Experimental
public final class ReflectionExecutables {

    private ReflectionExecutables() {
    }

    /**
     * The arguments of the constructor named by the caller. An introspection describes one constructor;
     * when the caller names another one of the type, its arguments are read from the constructor itself.
     *
     * @param introspection The introspection of the declaring type
     * @param constructor   The constructor
     * @return The arguments
     */
    public static Argument<?>[] constructorArguments(BeanIntrospection<?> introspection, Constructor<?> constructor) {
        Argument<?>[] arguments = introspection.getConstructorArguments();
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        if (arguments.length == parameterTypes.length) {
            boolean same = true;
            for (int i = 0; i < arguments.length; i++) {
                if (arguments[i].getType() != parameterTypes[i]) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return arguments;
            }
        }
        return AnnotationReflectionUtils.argumentsOf(constructor);
    }

    /**
     * The constructor named by the caller, with its arguments and its annotation metadata: the one the
     * introspection describes when it is that constructor, another one a {@link ReflectiveIntrospection}
     * knows, else one read from the constructor itself.
     *
     * @param introspection The introspection of the declaring type, can be {@code null}
     * @param constructor   The constructor
     * @param <T>           The declaring type
     * @return The bean constructor
     */
    public static <T> BeanConstructor<T> beanConstructor(@Nullable BeanIntrospection<T> introspection, Constructor<T> constructor) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        if (introspection != null) {
            List<BeanConstructor<T>> known = introspection instanceof ReflectiveIntrospection<T> reflective
                ? reflective.getConstructors()
                : List.of(introspection.getConstructor());
            for (BeanConstructor<T> candidate : known) {
                if (Arrays.equals(Argument.toClassArray(candidate.getArguments()), parameterTypes)) {
                    return candidate;
                }
            }
        }
        constructor.trySetAccessible();
        return new AbstractBeanConstructor<T>(constructor.getDeclaringClass(),
            ReflectionAnnotationMetadataBuilder.build(constructor),
            AnnotationReflectionUtils.argumentsOf(constructor)) {
            @Override
            public T instantiate(@Nullable Object... parameterValues) {
                try {
                    return constructor.newInstance(parameterValues == null ? new Object[0] : parameterValues);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot instantiate " + constructor.getDeclaringClass().getName(), e);
                }
            }
        };
    }

    /**
     * The executable method of the method named by the caller: the one of the bean definition when the
     * declaring type is a bean, else the one of the bean introspection, generated or reflective, else one
     * read from the method itself.
     *
     * @param locator      The locator of the executable methods of the beans
     * @param introspector The introspector
     * @param method       The method
     * @param <T>          The declaring type
     * @return The executable method
     */
    @SuppressWarnings("unchecked")
    public static <T> ExecutableMethod<T, Object> executableMethod(ExecutionHandleLocator locator,
                                                                   BeanIntrospector introspector,
                                                                   Method method) {
        Class<T> declaringType = (Class<T>) method.getDeclaringClass();
        Optional<ExecutableMethod<T, Object>> found = locator.findExecutableMethod(
            declaringType, method.getName(), method.getParameterTypes());
        // the locator answers for any bean of the type, a sub type overriding the method included: only the bean
        // declared by the type of the method is the method named
        if (found.isPresent() && found.get().getDeclaringType() == declaringType) {
            return found.get();
        }
        BeanIntrospection<T> introspection = introspector.findIntrospection(declaringType).orElse(null);
        if (introspection != null) {
            for (BeanMethod<T, Object> beanMethod : introspection.getBeanMethods()) {
                if (beanMethod.getName().equals(method.getName())
                    && Arrays.equals(Argument.toClassArray(beanMethod.getArguments()), method.getParameterTypes())) {
                    return new IntrospectedExecutableMethod<>(declaringType, beanMethod, method);
                }
            }
        }
        return new ReflectionExecutableMethod<>(declaringType, method);
    }
}
