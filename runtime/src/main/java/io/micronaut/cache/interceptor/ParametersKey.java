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
package io.micronaut.cache.interceptor;

import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArrayUtils;

import java.io.Serializable;
import java.util.Arrays;

/**
 * A key that uses the parameters of a method.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ParametersKey implements Serializable {

    public static final ParametersKey ZERO_ARG_KEY = new ParametersKey();

    private final Object[] params;
    private final int hashCode;

    /**
     * @param params Parameters of the method
     */
    public ParametersKey(Object... params) {
        if (ArrayUtils.isEmpty(params)) {
            this.params = ArrayUtils.EMPTY_OBJECT_ARRAY;
            this.hashCode = ClassUtils.EMPTY_OBJECT_ARRAY_HASH_CODE;
        } else {
            this.params = new Object[params.length];
            System.arraycopy(params, 0, this.params, 0, params.length);
            this.hashCode = Arrays.deepHashCode(this.params);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof ParametersKey && Arrays.deepEquals(params, ((ParametersKey) o).params);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return ParametersKey.class.getSimpleName() + ": " + ArrayUtils.toString(params);
    }
}
