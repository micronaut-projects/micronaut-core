/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.type;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;

/**
 * Default implementation of {@link MutableArgumentValue}.
 *
 * @param <V> The generic value
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultMutableArgumentValue<V> extends DefaultArgumentValue<V> implements MutableArgumentValue<V> {

    private V value;

    /**
     * @param argument The argument
     * @param value    The value
     */
    DefaultMutableArgumentValue(Argument<V> argument, V value) {
        super(argument, value);
        this.value = value;
    }

    @Override
    public void setValue(V value) {
        if (!getType().isInstance(value)) {
            this.value = value;
        } else {
            this.value = ConversionService.SHARED.convert(value, getType()).orElseThrow(() ->
                new IllegalArgumentException("Invalid value [" + value + "] for argument: " + this)
            );
        }
    }

    @Override
    public V getValue() {
        return value;
    }
}
