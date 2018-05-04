/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.security.filters;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import org.reactivestreams.Publisher;

/**
 * Describes a bean which attempts to read an {@link Authentication} from a {@link HttpRequest} being executed.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface AuthenticationFetcher extends Ordered {

    /**
     * Attempts to read an {@link Authentication} from a {@link HttpRequest} being executed.
     *
     * @param request {@link HttpRequest} being executed.
     * @return {@link Authentication} if found
     */
    Publisher<Authentication> fetchAuthentication(HttpRequest<?> request);
}
