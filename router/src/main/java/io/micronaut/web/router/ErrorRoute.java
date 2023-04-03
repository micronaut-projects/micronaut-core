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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;

import java.util.function.Predicate;

/**
 * Represents a {@link Route} that matches an exception.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ErrorRoute extends Route {

    @Override
    ErrorRouteInfo<Object, Object> toRouteInfo();

    /**
     * @return The type the exception originates from. Null if the error route is global.
     */
    @Nullable
    Class<?> originatingType();

    /**
     * @return The type of exception
     */
    Class<? extends Throwable> exceptionType();

    @Override
    ErrorRoute consumes(MediaType... mediaType);

    @Override
    ErrorRoute nest(Runnable nested);

    @Override
    ErrorRoute where(Predicate<HttpRequest<?>> condition);

    @Override
    ErrorRoute produces(MediaType... mediaType);
}
