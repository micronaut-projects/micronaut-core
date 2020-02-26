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
package io.micronaut.inject;

import io.micronaut.core.naming.Named;

import java.io.Serializable;

/**
 * An identifier for a {@link io.micronaut.context.annotation.Bean} that can be used as a key to uniquely identify an
 * instance.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanIdentifier extends CharSequence, Serializable, Named {

    /**
     * Create a new {@link BeanIdentifier} for the given id.
     *
     * @param id The id
     * @return The identifier
     */
    static BeanIdentifier of(String id) {
        return new DefaultBeanIdentifier(id);
    }
}
