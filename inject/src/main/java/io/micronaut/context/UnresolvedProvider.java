/*
 * Copyright 2017-2018 original authors
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

import javax.inject.Provider;

/**
 * A default component provider.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
class UnresolvedProvider<T> implements Provider<T> {

    private final Class<T> beanType;
    private final BeanContext context;

    /**
     * @param beanType The bean type
     * @param context  The bean context
     */
    UnresolvedProvider(Class<T> beanType, BeanContext context) {
        this.beanType = beanType;
        this.context = context;
    }

    @Override
    public T get() {
        return context.getBean(beanType);
    }
}
