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
package io.micronaut.context;

import io.micronaut.inject.BeanType;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * <p>Used to qualify which bean to select in the case of multiple possible options.</p>
 * <p>
 * <p>NOTE: When implementing a custom Qualifier you MUST implement {@link Object#hashCode()} and
 * {@link Object#equals(Object)} so that the qualifier can be used in comparisons and equality checks</p>
 *
 * @param <T> The qualifier type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Qualifier<T> {

    /**
     * @param beanType   The bean type
     * @param candidates The candidates
     * @param <BT>       The bean type subclass
     * @return The qualified candidate or null it it cannot be qualified
     */
    <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates);

    /**
     * Whether this qualifier contains the given qualifier.
     * @param qualifier The qualifier
     * @return True it does
     */
    default boolean contains(Qualifier<T> qualifier) {
        return equals(qualifier);
    }

    /**
     * Qualify the candidate from the stream of candidates.
     *
     * @param beanType   The bean type
     * @param candidates The candidates
     * @param <BT>       The bean type subclass
     * @return The qualified candidate or {@link Optional#empty()}
     */
    default <BT extends BeanType<T>> Optional<BT> qualify(Class<T> beanType, Stream<BT> candidates) {
        return reduce(beanType, candidates).findFirst();
    }
}
