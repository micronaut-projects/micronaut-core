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
package io.micronaut.validation.validator.extractors;

import javax.validation.valueextraction.ValueExtractor;

/**
 * No-op implementation that makes it easier to use with Lambdas.
 *
 * @author graemerocher
 * @since 1.2
 */
@FunctionalInterface
public interface SimpleValueReceiver extends ValueExtractor.ValueReceiver {
    @Override
    default void iterableValue(String nodeName, Object object) {

    }

    @Override
    default void indexedValue(String nodeName, int i, Object object) {

    }

    @Override
    default void keyedValue(String nodeName, Object key, Object object) {

    }
}
