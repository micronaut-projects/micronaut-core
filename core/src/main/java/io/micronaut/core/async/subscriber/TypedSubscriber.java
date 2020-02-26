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
package io.micronaut.core.async.subscriber;

import io.micronaut.core.type.Argument;

/**
 * A subscriber that is aware of the target type.
 *
 * @param <T> Type of TypedSubscriber
 * @author graemerocher
 * @since 1.0
 */
public abstract class TypedSubscriber<T> extends CompletionAwareSubscriber<T> {

    private final Argument<T> typeArgument;

    /**
     * Constructs a new {@link TypedSubscriber} for the given {@link Argument}.
     *
     * @param typeArgument The type argument
     */
    public TypedSubscriber(Argument<T> typeArgument) {
        this.typeArgument = typeArgument;
    }

    /**
     * @return The type argument
     */
    public Argument<T> getTypeArgument() {
        return typeArgument;
    }
}
