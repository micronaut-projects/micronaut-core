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
package io.micronaut.http.server.netty.converters;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.format.Error;
import io.micronaut.http.server.exceptions.format.ErrorContext;
import io.micronaut.http.server.exceptions.format.ErrorResponseFactory;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * Handles exceptions of type {@link UnsatisfiedRouteException}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Produces
public class UnsatisfiedRouteHandler implements ExceptionHandler<UnsatisfiedRouteException, HttpResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(UnsatisfiedRouteHandler.class);

    private final ErrorResponseFactory<?> responseFactory;

    @Deprecated
    public UnsatisfiedRouteHandler() {
        this.responseFactory = null;
    }

    @Inject
    public UnsatisfiedRouteHandler(ErrorResponseFactory<?> responseFactory) {
        this.responseFactory = responseFactory;
    }

    @Override
    public HttpResponse handle(HttpRequest request, UnsatisfiedRouteException exception) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("{} (Bad Request): {}", request, exception.getMessage());
        }
        MutableHttpResponse<?> response = HttpResponse.badRequest();
        if (responseFactory != null) {
            return responseFactory.createResponse(ErrorContext.builder(request)
                    .cause(exception)
                    .error(new Error() {
                        @Override
                        public String getMessage() {
                            return exception.getMessage();
                        }

                        @Override
                        public Optional<String> getPath() {
                            return Optional.of('/' + exception.getArgument().getName());
                        }
                    })
                    .build(), response);
        } else {
            return response.body(new JsonError(exception.getMessage())
                    .path('/' + exception.getArgument().getName())
                    .link(Link.SELF, Link.of(request.getUri())));
        }
    }
}
