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
import io.micronaut.core.type.Argument;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.form.FormData;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * A locator route whose locator reads the body of the request, declared with
 * {@link HttpRouteScope#locator(String)}: the body stage, like {@link HttpRouteSpec#body(Argument)},
 * {@link HttpRouteSpec#body()} and {@link HttpRouteSpec#form()} of a route, followed by the
 * locator, see {@link LocatorBodySpec}.
 *
 * <pre>{@code
 * routes.locator("/accounts/{id}")
 *     .form()
 *     .locate((request, pathVariables, form) -> new AccountResource(form.getString("token")), AccountResource::routes);
 * }</pre>
 *
 * <p>The locator reads a {@link AsyncRequestBody#copy() copy} of the body: the located route
 * reads the body again, with its own body stage, {@code @Body} argument or form. The form of a
 * copy is the one form of the request, which the located route receives too, without parsing it
 * again. Since the body is read twice, it is held to {@code micronaut.server.max-request-buffer-size}
 * as a whole, with {@code 413} for a larger body. What the locator's read left open is released
 * when its target is located.</p>
 *
 * <p>Otherwise the locator route is a locator route of
 * {@link HttpRouteScope#locateAsync(String, AsyncLocatorHandler, java.util.function.Function)}:
 * the target is located once per request, and the request is matched again once it is. A request
 * the client abandons while its body is read stops waiting for the locator.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface LocatorSpec permits DefaultLocatorSpec {

    /**
     * The locator receives the body decoded to a type, like a handler of
     * {@link HttpRouteSpec#body(Argument)}: a request without a body is located with {@code null}
     * for a {@link Argument#isNullable() nullable} type, and fails with {@code 400} otherwise.
     *
     * @param bodyType The body type
     * @param <B>      The body type
     * @return The locator stage
     */
    <B extends @Nullable Object> LocatorBodySpec<B> body(Argument<B> bodyType);

    /**
     * Like the variant taking an {@link Argument}, with the body type as a class: {@code body(Argument.of(bodyType))}.
     *
     * @param bodyType The body type
     * @param <B>      The body type
     * @return The locator stage
     */
    default <B> LocatorBodySpec<B> body(Class<B> bodyType) {
        return body(Argument.of(Objects.requireNonNull(bodyType, "bodyType")));
    }

    /**
     * The locator reads the body itself, as an {@link AsyncRequestBody}: a copy of the body of the
     * request, which the locator may read once, before or while its stage completes. What its
     * read left open is released when the stage completes, except a form it started to read:
     * the form of a copy is the one form of the request, which the request keeps reading and
     * releases, and the route waits for it, as it waits for a form one of its arguments reads.
     *
     * @return The locator stage
     */
    LocatorBodySpec<AsyncRequestBody> body();

    /**
     * The locator receives the whole submitted form, {@code application/x-www-form-urlencoded} or
     * {@code multipart/form-data}, as {@link FormData}, like a handler of
     * {@link HttpRouteSpec#form()}. The located route that reads the form receives the same form.
     *
     * <p>The form is read from a copy of the body: the server must decode a form from a split
     * of the bytes of the request, see
     * {@link io.micronaut.http.form.FormCapableHttpRequest#getRawFormFields(io.micronaut.http.body.ByteBody)},
     * as the Netty server does. On a server that does not, a form request under the locator fails.</p>
     *
     * <p>The locator route still matches every request under its prefix: a request whose
     * content type is not a form, e.g. a {@code GET}, a {@code HEAD}, a CORS preflight or a JSON
     * body for a located route, is located with {@link FormData#empty() the empty form}, like a
     * JAX-RS {@code @FormParam} without a form entity, and its body is neither read nor
     * buffered: the located routes decide what they consume. A form that fails to read, e.g. a
     * malformed or too large multipart body, fails the request; it does not become empty.</p>
     *
     * @return The locator stage
     */
    LocatorBodySpec<FormData> form();
}
