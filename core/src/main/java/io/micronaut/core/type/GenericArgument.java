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
package io.micronaut.core.type;

import io.micronaut.core.annotation.AnnotationMetadata;

/**
 * Captures a generic {@link Argument}.
 * <p>
 * Example usage: <code>new GenericArgument&lt;List&lt;T&gt;&gt;() {}</code>
 *
 * @param <T> generic argument type
 * @author Vladimir Kulev
 * @since 1.0
 */
public abstract class GenericArgument<T> extends DefaultArgument<T> {

    /**
     * Default constructor.
     */
    protected GenericArgument() {
        super(null, null, AnnotationMetadata.EMPTY_METADATA);
    }

}
