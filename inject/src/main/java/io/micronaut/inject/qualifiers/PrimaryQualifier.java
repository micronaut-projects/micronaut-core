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

/**
 * A qualifier to look up a primary bean.
 *
 * @param <T> The generic type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
@SuppressWarnings("java:S1845")
public final class PrimaryQualifier<T> extends FilteringQualifier<T> {

    @SuppressWarnings({"rawtypes", "java:S1845"})
    public static final PrimaryQualifier INSTANCE = new PrimaryQualifier();

    private PrimaryQualifier() {
    }

    @Override
    public boolean doesQualify(Class<T> beanType, BeanType<T> candidate) {
        if (candidate.isPrimary() || QualifierUtils.match(candidate, this)) {
            return true;
        }
        return QualifierUtils.matchByCandidateName(candidate, beanType, Qualifier.PRIMARY);
    }

    @Override
    public boolean doesQualify(Class<T> beanType, QualifiedBeanType<T> candidate) {
        if (candidate.isPrimary() || QualifierUtils.matchQualified(candidate, this)) {
            return true;
        }
        return QualifierUtils.matchByCandidateName(candidate, beanType, Qualifier.PRIMARY);
    }

    @Override
    public String toString() {
        return "@Primary";
    }

    /**
     * Generified way to get the primary instance.
     * @return The instance
     * @param <T1> The generic type
     * @since 3.6.0
     */
    @SuppressWarnings("unchecked")
    public static <T1> PrimaryQualifier<T1> instance() {
        return PrimaryQualifier.INSTANCE;
    }
}
