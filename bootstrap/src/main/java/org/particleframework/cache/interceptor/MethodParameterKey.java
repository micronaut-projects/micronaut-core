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
package org.particleframework.cache.interceptor;

import org.particleframework.core.reflect.ClassUtils;
import org.particleframework.core.util.ArrayUtils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A key that uses the parameters of a method
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class MethodParameterKey implements Serializable {

    public static final MethodParameterKey ZERO_ARG_KEY = new MethodParameterKey();

    private final Object[] params;
    private final int hashCode;

    public MethodParameterKey(Object... params) {
        if(ArrayUtils.isEmpty(params)) {
            this.params = ClassUtils.EMPTY_OBJECT_ARRAY;
            this.hashCode = ClassUtils.EMPTY_OBJECT_ARRAY_HASH_CODE;
        }
        else {
            this.params = new Object[params.length];
            System.arraycopy(params, 0, this.params, 0, params.length);
            this.hashCode = Arrays.deepHashCode(this.params);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof MethodParameterKey && Arrays.deepEquals(params, ((MethodParameterKey) o).params);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return MethodParameterKey.class.getSimpleName() + ": " + ArrayUtils.toString(params);
    }
}
