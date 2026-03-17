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
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.QualifiedBeanType;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A qualifier to look up any type.
 *
 * @param <T> The generic type
 * @since 3.0.0
 */
@Internal
public final class AnyQualifier<T> extends FilteringQualifier<T> {
    @SuppressWarnings("rawtypes")
    public static final AnyQualifier INSTANCE = new AnyQualifier();

    private AnyQualifier() {
    }

    @Override
    public boolean doesQualify(Class<T> beanType, BeanType<T> candidate) {
        return true;
    }

    @Override
    public boolean doesQualify(Class<T> beanType, QualifiedBeanType<T> candidate) {
        return true;
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates;
    }

    @Override
    public boolean contains(Qualifier<T> qualifier) {
        return true;
    }

    @Override
    public <BT extends BeanType<T>> Optional<BT> qualify(Class<T> beanType, Stream<BT> candidates) {
        return candidates.findFirst();
    }

    @Override
    public boolean doesQualify(Class<T> beanType, Collection<? extends BeanType<T>> candidates) {
        return !candidates.isEmpty();
    }

    @Override
    public <BT extends BeanType<T>> Collection<BT> filter(Class<T> beanType, Collection<BT> candidates) {
        return candidates;
    }

    @Override
    public String toString() {
        return "@Any";
    }
}
