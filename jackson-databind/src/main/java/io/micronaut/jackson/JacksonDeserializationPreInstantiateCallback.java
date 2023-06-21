/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.jackson;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanIntrospection;

/**
 * The pre instantiate callback.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Experimental
public interface JacksonDeserializationPreInstantiateCallback {

    /**
     * The callback before the bean is constructed.
     *
     * @param beanIntrospection The bean introspection
     * @param arguments         The argument values
     */
    void preInstantiate(BeanIntrospection<?> beanIntrospection, Object... arguments);

}
