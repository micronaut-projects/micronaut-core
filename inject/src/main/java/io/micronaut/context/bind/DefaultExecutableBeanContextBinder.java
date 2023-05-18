/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.context.bind;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.bind.ArgumentBinderRegistry;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of {@link ExecutableBeanContextBinder}.
 *
 * @since 4.0.0
 */
public final class DefaultExecutableBeanContextBinder implements ExecutableBeanContextBinder {

    @Override
    public <T, R> BoundExecutable<T, R> bind(Executable<T, R> target, ArgumentBinderRegistry<BeanContext> registry, BeanContext source) throws UnsatisfiedArgumentException {
        return bind(target, source);
    }

    @Override
    public <T, R> BoundExecutable<T, R> tryBind(Executable<T, R> target, ArgumentBinderRegistry<BeanContext> registry, BeanContext source) {
        Argument<?>[] arguments = target.getArguments();
        if (arguments.length == 0) {
            return new ContextBoundExecutable<>(target, ArrayUtils.EMPTY_OBJECT_ARRAY);
        }
        Object[] bound = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
            Optional<String> v = argument.getAnnotationMetadata().stringValue(Value.class);
            if (v.isPresent() && source instanceof ApplicationContext applicationContext) {
                bound[i] = applicationContext.getEnvironment().getProperty(v.get(), argument)
                    .orElse(null);
            } else {
                v = argument.getAnnotationMetadata().stringValue(Property.class, "name");
                if (v.isPresent() && source instanceof ApplicationContext applicationContext) {
                    bound[i] = applicationContext.getEnvironment()
                        .getProperty(v.get(), argument).orElse(null);
                } else {
                    bound[i] = source.findBean(argument, resolveQualifier(argument))
                        .orElse(null);
                }
            }
        }
        return new ContextBoundExecutable<>(target, bound);
    }

    @Override
    public <T, R> BoundExecutable<T, R> bind(Executable<T, R> target, BeanContext source) throws UnsatisfiedArgumentException {
        Argument<?>[] arguments = target.getArguments();
        if (arguments.length == 0) {
            return new ContextBoundExecutable<>(target, ArrayUtils.EMPTY_OBJECT_ARRAY);
        }
        Object[] bound = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
            Optional<String> v = argument.getAnnotationMetadata().stringValue(Value.class);
            if (v.isPresent() && source instanceof ApplicationContext applicationContext) {
                Optional<String> finalV = v;
                bound[i] = applicationContext.getEnvironment().getProperty(v.get(), argument)
                    .orElseThrow(() ->
                        new UnsatisfiedArgumentException(argument, "Unresolvable property specified to @Value: " + finalV.get())
                    );
            } else {
                v = argument.getAnnotationMetadata().stringValue(Property.class, "name");
                if (v.isPresent() && source instanceof ApplicationContext applicationContext) {
                    Optional<String> finalV1 = v;
                    bound[i] = applicationContext.getEnvironment()
                        .getProperty(v.get(), argument).orElseThrow(() ->
                            new UnsatisfiedArgumentException(argument, "Unresolvable property specified to @Value: " + finalV1.get())
                        );
                } else {
                    bound[i] = source.findBean(argument, resolveQualifier(argument))
                        .orElseThrow(() ->
                            new UnsatisfiedArgumentException(argument, "Unresolvable bean argument: " + argument)
                        );
                }
            }
        }
        return new ContextBoundExecutable<>(target, bound);
    }

    /**
     * Build a qualifier for the given argument.
     * @param argument The argument
     * @param <T> The type
     * @return The resolved qualifier
     */
    @SuppressWarnings("unchecked")
    private static <T> Qualifier<T> resolveQualifier(Argument<?> argument) {
        AnnotationMetadata annotationMetadata = Objects.requireNonNull(argument, "Argument cannot be null").getAnnotationMetadata();
        boolean hasMetadata = annotationMetadata != AnnotationMetadata.EMPTY_METADATA;

        List<String> qualifierTypes = hasMetadata ? AnnotationUtil.findQualifierAnnotationsNames(annotationMetadata) : Collections.emptyList();
        if (CollectionUtils.isNotEmpty(qualifierTypes)) {
            if (qualifierTypes.size() == 1) {
                return Qualifiers.byAnnotation(
                    annotationMetadata,
                    qualifierTypes.iterator().next()
                );
            } else {
                final Qualifier[] qualifiers = qualifierTypes
                    .stream().map((type) -> Qualifiers.byAnnotation(annotationMetadata, type))
                    .toArray(Qualifier[]::new);
                return Qualifiers.<T>byQualifiers(
                    qualifiers
                );
            }
        }
        return null;
    }

    private record ContextBoundExecutable<T, R> (Executable<T, R> target, Object[] bound) implements BoundExecutable<T, R> {
        @Override
        public Executable<T, R> getTarget() {
            return target;
        }

        @Override
        public R invoke(T instance) {
            return target.invoke(instance, bound);
        }

        @Override
        public Object[] getBoundArguments() {
            return bound;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ContextBoundExecutable<?, ?> that = (ContextBoundExecutable<?, ?>) o;
            return target.equals(that.target) && Arrays.equals(bound, that.bound);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(target);
            result = 31 * result + Arrays.hashCode(bound);
            return result;
        }

        @Override
        public String toString() {
            return "ContextBoundExecutable{" +
                "target=" + target +
                ", bound=" + Arrays.toString(bound) +
                '}';
        }
    }
}
