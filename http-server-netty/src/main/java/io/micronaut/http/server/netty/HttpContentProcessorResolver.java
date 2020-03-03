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
package io.micronaut.http.server.netty;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.core.type.Argument;
import io.micronaut.web.router.RouteMatch;

import javax.annotation.Nonnull;

/**
 * Responsible for determining which {@link HttpContentProcessor} to use to process
 * the body of the request.
 *
 * @author James Kleeh
 * @since 1.3.0
 */
@DefaultImplementation(DefaultHttpContentProcessorResolver.class)
public interface HttpContentProcessorResolver {

    /**
     * Resolves the processor for the given request and route.
     *
     * @param request The request
     * @param route   The matched route
     * @return The content processor
     */
    @Nonnull
    HttpContentProcessor<?> resolve(@Nonnull NettyHttpRequest<?> request, @Nonnull RouteMatch<?> route);

    /**
     * Resolves the processor for the given request and body argument.
     *
     * @param request  The request
     * @param bodyType The body argument
     * @return The content processor
     */
    @Nonnull
    HttpContentProcessor<?> resolve(@Nonnull NettyHttpRequest<?> request, @Nonnull Argument<?> bodyType);

    /**
     * Resolves the processor for the given request.
     *
     * @param request  The request
     * @return The content processor
     */
    @Nonnull
    HttpContentProcessor<?> resolve(@Nonnull NettyHttpRequest<?> request);
}
