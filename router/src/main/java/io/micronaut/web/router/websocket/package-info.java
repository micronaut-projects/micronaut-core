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
/**
 * WebSocket endpoints declared as routes of handler functions, see
 * {@link io.micronaut.web.router.websocket.WebSocketRouteSpec}.
 *
 * <p>The types of this package use {@code micronaut-websocket}, which the router has as an optional
 * dependency: they are only loaded by an application that declares a WebSocket route, and no
 * other type of the router has them in its signatures, so the router works without
 * {@code micronaut-websocket}, e.g. for Groovy, which reflects on the generic signatures of the
 * route builder.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@NullMarked
package io.micronaut.web.router.websocket;

import org.jspecify.annotations.NullMarked;
