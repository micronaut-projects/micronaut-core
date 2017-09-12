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
package org.particleframework.web.router;

import org.particleframework.http.HttpRequest;
import org.particleframework.http.MediaType;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Represents a {@link Route} that matches an exception
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ErrorRoute extends Route {
    /**
     * @return The type the exception originates from. Null if the error route is global.
     */
    Class<?> originatingType();
    /**
     * @return The type of exception
     */
    Class<? extends Throwable> exceptionType();

    /**
     * Match the given exception
     *
     * @param exception The exception to match
     * @return The route match
     */
    <T> Optional<RouteMatch<T>> match(Throwable exception);

    /**
     * Match the given exception
     *
     * @param originatingClass The class where the error originates from
     * @param exception The exception to match
     * @return The route match
     */
    <T> Optional<RouteMatch<T>> match(Class originatingClass, Throwable exception);

    @Override
    ErrorRoute accept(MediaType... mediaType);

    @Override
    ErrorRoute nest(Runnable nested);

    @Override
    ErrorRoute where(Predicate<HttpRequest> condition);
}
