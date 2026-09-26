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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.PathVariables;
import io.micronaut.http.form.FormData;

/**
 * A route handler of the routes of a located target that receives the target and the
 * submitted form, see {@link LocatedHttpRouteBuilder}: otherwise the same as a
 * {@link FormRequestHandler}.
 *
 * @param <T> The type of the located target
 * @author Denis Stepanov
 * @since 5.3.0
 * @see LocatedHttpRouteBuilder#handleForm(io.micronaut.http.HttpMethod, String, LocatedFormRequestHandler)
 */
@Experimental
@FunctionalInterface
public interface LocatedFormRequestHandler<T> {

    /**
     * Handle the request.
     *
     * @param request       The request
     * @param pathVariables The path variables of the prefixes of the locators and of the route
     * @param target        The located target
     * @param form          The submitted form
     * @return The response
     * @throws Exception An error, handled by the error routes like a controller error
     */
    HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables, T target, FormData form) throws Exception;
}
