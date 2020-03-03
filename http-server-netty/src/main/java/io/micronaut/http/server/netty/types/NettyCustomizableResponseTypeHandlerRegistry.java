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
package io.micronaut.http.server.netty.types;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;

import java.util.Optional;

/**
 * Represents a registry of {@link NettyCustomizableResponseTypeHandler} and finds the correct handler based on
 * the type.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
@Experimental
public interface NettyCustomizableResponseTypeHandlerRegistry {

    /**
     * Finds the first type handler that supports the given type.
     *
     * @param type The type to search for
     * @return An optional {@link NettyCustomizableResponseTypeHandler}
     * @see NettyCustomizableResponseTypeHandler#supports(Class)
     */
    Optional<NettyCustomizableResponseTypeHandler> findTypeHandler(Class<?> type);
}
