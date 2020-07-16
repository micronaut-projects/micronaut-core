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
package io.micronaut.inject;

import javax.inject.Provider;
import java.util.Map;

/**
 * An extended version of the {@link Provider} interface for {@link ParametrizedBeanFactory}.
 *
 * @param <T> The type
 * @author graemerocher
 * @since 1.0
 */
public interface ParametrizedProvider<T> extends Provider<T> {

    /**
     * @param argumentValues The argument values to use
     * @return The bean
     */
    T get(Map<String, Object> argumentValues);

    /**
     * @param argumentValues The argument values to use
     * @return The bean
     */
    T get(Object... argumentValues);

    @Override
    default T get() {
        return get((Map<String, Object>) null);
    }
}
