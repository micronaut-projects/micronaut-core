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
package io.micronaut.inject.qualifiers;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.QualifiedBeanType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

/**
 * A variation of {@link Qualifier} that is a simple filter.
 *
 * @param <T> The qualifier type
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
public abstract class FilteringQualifier<T> implements Qualifier<T> {

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> doesQualify(beanType, candidate));
    }

    @Override
    public boolean doesQualify(Class<T> beanType, Collection<? extends BeanType<T>> candidates) {
        for (BeanType<T> candidate : candidates) {
            if (doesQualify(beanType, candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean doesQualifyQualified(Class<T> beanType, Collection<? extends QualifiedBeanType<T>> candidates) {
        for (QualifiedBeanType<T> candidate : candidates) {
            if (doesQualify(beanType, candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <BT extends BeanType<T>> Collection<BT> filter(Class<T> beanType, Collection<BT> candidates) {
        int size = candidates.size();
        if (size == 1) {
            return doesQualify(beanType, candidates.iterator().next()) ? candidates : Collections.emptyList();
        }
        Collection<BT> result = new ArrayList<>(size);
        for (BT candidate : candidates) {
            if (doesQualify(beanType, candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    @Override
    public <BT extends QualifiedBeanType<T>> Collection<BT> filterQualified(Class<T> beanType, Collection<BT> candidates) {
        int size = candidates.size();
        if (size == 1) {
            return doesQualify(beanType, candidates.iterator().next()) ? candidates : Collections.emptyList();
        }
        Collection<BT> result = new ArrayList<>(size);
        for (BT candidate : candidates) {
            if (doesQualify(beanType, candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
