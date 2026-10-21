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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.MessageBodyHandlerRegistry;

/**
 * What a route locator that reads the body of the request needs, see
 * {@link io.micronaut.web.router.builder.LocatorSpec}.
 *
 * @param context      Provides the body of a request, see {@link RouteConditionContext#locatorBody}
 * @param bodyHandlers The message body readers, which decode a body without a content type
 *                     like a handler route does
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public record LocatorSupport(RouteConditionContext context, MessageBodyHandlerRegistry bodyHandlers) {
}
