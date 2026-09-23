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
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;

/**
 * A status route to a handler function, to configure after it was added with the
 * {@link HttpRouteBuilder}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface StatusRouteSpec {

    /**
     * Produce these media types, like {@code @Produces} on an {@code @Error} method.
     *
     * @param mediaTypes The media types
     * @return The route
     */
    StatusRouteSpec produces(MediaType... mediaTypes);

    /**
     * Declare the type of the body of the responses of the handler, like the return type
     * {@code HttpResponse<R>} of an {@code @Error(status = ...)} method: the message body writer is selected
     * for the declared type, with its type arguments and annotations, instead of the runtime
     * class of the body. See {@link HttpRouteSpec#responseType(Argument)}.
     *
     * @param responseType The type of the body of the response
     * @return The route
     * @since 5.3.0
     */
    StatusRouteSpec responseType(Argument<?> responseType);
}
