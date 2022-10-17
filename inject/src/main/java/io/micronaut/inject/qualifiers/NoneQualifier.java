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
package io.micronaut.inject.qualifiers;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;

import java.util.stream.Stream;

/**
 * A qualifier to lookup beans without any qualifier.
 *
 * @param <T> The generic type
 * @since 3.8.0
 */
@Internal
final class NoneQualifier<T> implements Qualifier<T> {
    @SuppressWarnings("rawtypes")
    public static final NoneQualifier INSTANCE = new NoneQualifier();

    private NoneQualifier() {
    }

    @Override
    public <B extends BeanType<T>> Stream<B> reduce(Class<T> beanType, Stream<B> candidates) {
        return candidates.filter(candidate -> {
            if (candidate instanceof BeanDefinition) {
                return ((BeanDefinition<?>) candidate).getDeclaredQualifier() == null;
            }
            return !candidate.getAnnotationMetadata().hasDeclaredAnnotation(AnnotationUtil.QUALIFIER);
        });
    }

    @Override
    public String toString() {
        return "None";
    }
}
