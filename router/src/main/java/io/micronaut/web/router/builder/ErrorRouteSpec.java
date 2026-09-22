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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.MediaType;

/**
 * An error route to a handler function, to configure after it was added with the
 * {@link HttpRouteBuilder}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface ErrorRouteSpec {

    /**
     * Produce these media types, like {@code @Produces} on an {@code @Error} method.
     *
     * @param mediaTypes The media types
     * @return The route
     */
    ErrorRouteSpec produces(MediaType... mediaTypes);
}
