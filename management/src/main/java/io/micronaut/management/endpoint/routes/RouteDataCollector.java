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
package io.micronaut.management.endpoint.routes;

import io.micronaut.web.router.UriRoute;
import org.reactivestreams.Publisher;

import java.util.stream.Stream;

/**
 * <p>Used to respond with route information used for the {@link RoutesEndpoint}.</p>
 *
 * @param <T> The type
 * @author James Kleeh
 * @since 1.0
 */
public interface RouteDataCollector<T> {

    /**
     * @param routes A java stream of uri routes
     * @return A publisher that returns data representing all of
     * the given routes.
     */
    Publisher<T> getData(Stream<UriRoute> routes);
}
