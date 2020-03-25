/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

/**
 * Represents an {@link Argument} as a {@link BeanType}. Useful in combination with qualifiers.
 *
 * @author graemerocher
 * @since 1.2
 * @param <T> The generic type
 */
public final class ArgumentBeanType<T> implements BeanType<T>, Argument<T> {

    private final Argument<T> argument;

    /**
     * Default constructor.
     * @param argument The argument
     */
    public ArgumentBeanType(@NonNull Argument<T> argument) {
        ArgumentUtils.requireNonNull("argument", argument);
        this.argument = argument;
    }

    @Override
    public String getName() {
        return argument.getName();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return argument.getAnnotationMetadata();
    }

    @Override
    public Map<String, Argument<?>> getTypeVariables() {
        return argument.getTypeVariables();
    }

    @Override
    public Class<T> getType() {
        return argument.getType();
    }

    @Override
    public boolean equalsType(Argument<?> other) {
        return argument.equals(other);
    }

    @Override
    public int typeHashCode() {
        return argument.getType().hashCode();
    }

    @Override
    public boolean isPrimary() {
        return true;
    }

    @Override
    public Class<T> getBeanType() {
        return argument.getType();
    }

    @Override
    public boolean isEnabled(BeanContext context) {
        return true;
    }
}
