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
package io.micronaut.core.value;

import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link OptionalMultiValues }.
 *
 * @param <V> The generic value
 * @author Graeme Rocher
 * @since 1.0
 */
class OptionalMultiValuesMap<V> extends OptionalValuesMap<List<V>> implements OptionalMultiValues<V> {

    /**
     * @param type   The type
     * @param values The values
     */
    public OptionalMultiValuesMap(Class<?> type, Map<CharSequence, ?> values) {
        super(type, values);
    }
}
