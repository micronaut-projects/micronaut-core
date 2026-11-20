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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Internal;
import io.micronaut.web.router.builder.AsyncRequestHandler;

/**
 * A fallback of a circuit breaker that relays the request to another upstream, e.g. of
 * {@code fallback-uri}: like a {@link ProxyHandler}, it needs the body of the request, so a body
 * an attempt sent and nothing kept fails the request with the failure of the upstream instead.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface FailoverHandler extends AsyncRequestHandler {
}
