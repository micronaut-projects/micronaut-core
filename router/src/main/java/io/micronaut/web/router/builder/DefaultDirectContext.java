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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.direct.DirectRequest;

/**
 * A {@link DirectContext} given its values, see {@link DirectContext#of}: the lookup of the
 * direct routes gives a function its match instead, see {@link DirectRouteTable.FunctionMatch}.
 *
 * @param request       The request
 * @param pathVariables The path variables of the route
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record DefaultDirectContext(DirectRequest request, PathVariables pathVariables) implements DirectContext {
}
