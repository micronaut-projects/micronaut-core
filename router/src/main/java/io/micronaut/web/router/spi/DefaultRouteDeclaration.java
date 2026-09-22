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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpMethod;

/**
 * An {@link IndexedRouteDeclaration} declared in code, with the keys computed from the template.
 *
 * @param httpMethod         The HTTP method
 * @param uriTemplate        The URI template
 * @param requiredPathPrefix The literal prefix of the matched paths
 * @param rawLength          The length of the literal parts
 * @param pathVariableCount  The number of path variables
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record DefaultRouteDeclaration(HttpMethod httpMethod,
                               String uriTemplate,
                               String requiredPathPrefix,
                               int rawLength,
                               int pathVariableCount) implements IndexedRouteDeclaration {
}
