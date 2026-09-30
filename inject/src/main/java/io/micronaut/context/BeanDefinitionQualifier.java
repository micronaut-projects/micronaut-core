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
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;

import java.util.stream.Stream;

/**
 * A qualifier that narrows the candidates to one bean definition.
 *
 * <p>Two qualifiers of equal definitions are equal, so that a key built from the qualifier, such as the one a
 * custom scope stores a bean under, is the same every time the definition is resolved.</p>
 *
 * @param definition The definition
 * @param <T>        The bean type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record BeanDefinitionQualifier<T>(BeanDefinition<? extends T> definition) implements Qualifier<T> {

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(definition::equals);
    }
}
