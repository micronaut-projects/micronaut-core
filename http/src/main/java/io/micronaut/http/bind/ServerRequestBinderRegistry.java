/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.bind;

import io.micronaut.core.annotation.Internal;
import io.micronaut.context.BeanContext;

import java.util.Optional;

/**
 * The {@link RequestBinderRegistry} a server binds the arguments of its routes with, e.g. with the
 * binders of the form fields of its requests. The request filter methods of the server filters
 * bind their arguments with it too, like the routes.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface ServerRequestBinderRegistry extends RequestBinderRegistry {
    /**
     * Select the server default while retaining a transport's legacy registry replacement.
     *
     * @param context The bean context
     * @return The registry selected for server routes and filters
     */
    static Optional<RequestBinderRegistry> find(BeanContext context) {
        Optional<RequestBinderRegistry> legacy = context.findBean(RequestBinderRegistry.class);
        if (legacy.isPresent() && legacy.get().getClass() != DefaultRequestBinderRegistry.class) {
            return legacy;
        }
        return context.findBean(ServerRequestBinderRegistry.class).map(RequestBinderRegistry.class::cast).or(() -> legacy);
    }
}
