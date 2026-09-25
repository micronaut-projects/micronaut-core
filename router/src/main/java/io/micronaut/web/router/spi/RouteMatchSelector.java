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
package io.micronaut.web.router.spi;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.UriRouteMatch;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Selects among the routes of a {@link io.micronaut.http.uri.spi.RouteTemplateEngine route template
 * engine} that match a request, instead of the Micronaut route selection policy, and negotiates
 * the media type of the response. It is implemented by the engine itself: the router uses it when
 * the engine of the templates of the candidate routes is an instance of this interface.
 *
 * <p>The router calls it when every route that matches the path of a request, and accepts its
 * method, port, conditions, content type ({@link io.micronaut.web.router.RouteInfo#doesConsume})
 * and accepted types ({@link io.micronaut.web.router.RouteInfo#doesProduce}), has a template of
 * the engine (for the routes of such an engine, the types are compatible when one matches the
 * other, without the parameters: {@code Accept: image/*} reaches the routes that produce
 * {@code image/png} and {@code image/*}; when none is compatible the router answers {@code 406}
 * or {@code 415} as for other routes), even when there is only one such route, instead of the Micronaut resolution of
 * ambiguous routes by the media types and the specificity of the templates. When the candidates
 * are of different engines, the Micronaut policy selects, and the selector is not called. For
 * example a JAX-RS engine selects by the specificity of JAX-RS (section 3.7.2), then by the
 * content type of the request against {@code @Consumes} and the accepted types, with their
 * quality, against {@code @Produces}, with their {@code qs}.</p>
 *
 * <p>When the router finds the routes of the path of every HTTP method
 * ({@link io.micronaut.web.router.Router#findAny(HttpRequest)}), e.g. for the {@code 405} status
 * and its {@code Allow} header, or a CORS preflight request, it calls the selector for the matches
 * of each method, given as above for a request of that method, and a route of the engine counts only
 * when it is selected. A route the selector does not select for the method of the request is not
 * found, and a method none of whose routes is selected is not allowed.</p>
 *
 * <p>The matches are normal route matches: filters, argument binding and the annotations of the
 * route apply to the selected match as to any other. The negotiated media type of a selection
 * reaches the handler as {@link UriRouteMatch#getSelectedMediaType()} and
 * {@link io.micronaut.web.router.builder.PathVariables#selectedMediaType()}, and is the content
 * type of the response when the handler does not set one.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface RouteMatchSelector {

    /**
     * Select among the matches of a request.
     *
     * @param request The request
     * @param matches The matches of the path, of routes of this engine, in the order of the route
     *                table: at least one
     * @return The selected matches, each one of the given matches: one to route the request, none
     * for no route ({@code 404}, or the status the router answers for a path no route matches), and
     * more than one for an ambiguous request
     */
    List<Selection> select(HttpRequest<?> request, List<UriRouteMatch<?, ?>> matches);

    /**
     * A selected match.
     *
     * @param match             One of the matches given to the selector
     * @param responseMediaType The negotiated media type of the response, or {@code null} to let
     *                          the server choose as for other routes
     */
    @Experimental
    record Selection(UriRouteMatch<?, ?> match, @Nullable MediaType responseMediaType) {

        /**
         * @param match             One of the matches given to the selector
         * @param responseMediaType The negotiated media type of the response, or {@code null}
         */
        public Selection {
            Objects.requireNonNull(match, "match");
        }

        /**
         * @param match A match, without a negotiated media type
         * @return The selection
         */
        public static Selection of(UriRouteMatch<?, ?> match) {
            return new Selection(match, null);
        }

        /**
         * @param match             A match
         * @param responseMediaType The negotiated media type of the response
         * @return The selection
         */
        public static Selection of(UriRouteMatch<?, ?> match, @Nullable MediaType responseMediaType) {
            return new Selection(match, responseMediaType);
        }
    }
}
