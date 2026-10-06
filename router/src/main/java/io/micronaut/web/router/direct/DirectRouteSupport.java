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
package io.micronaut.web.router.direct;

import io.micronaut.core.annotation.Experimental;

/**
 * The capability of a server runtime to answer direct routes: a runtime that looks up the
 * {@link DirectRouteLookup} for each request it receives, as {@link DirectRouteLookup} describes, declares
 * a bean of this type, e.g. a {@code @Singleton} of its module. When the
 * application declares a direct route and no bean of this type exists, e.g. on a runtime that
 * has no hook to answer a request before it creates the {@link io.micronaut.http.HttpRequest},
 * the routes fail to build, and the application fails to start: a direct route is never served
 * as an ordinary route, with the filters it was declared to skip.
 *
 * <p>The bean has no method: its presence is the capability. A runtime that declares it answers
 * every direct route, the asynchronous ones included, see {@link DirectRouteLookup#PENDING}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface DirectRouteSupport {
}
