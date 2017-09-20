/*
 * Copyright 2017 original authors
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
package org.particleframework.inject;

import org.particleframework.core.annotation.Internal;

/**
 * Default implementation of {@link MutableArgumentValue}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultMutableArgumentValue<V> extends DefaultArgumentValue<V> implements MutableArgumentValue<V> {

    private V value;

    DefaultMutableArgumentValue(Argument<V> argument, V value) {
        super(argument, value);
        this.value = value;
    }

    @Override
    public void setValue(V value) {
        if(!getType().isInstance(value)) {
            throw new IllegalArgumentException("Invalid value ["+value+"] for argument: " + this);
        }
        this.value = value;
    }

    @Override
    public V getValue() {
        return value;
    }
}
