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
package io.micronaut.http.server.tck.netty.precompiled;

import io.micronaut.web.router.annotation.PrecompiledHttpRoutes;

/**
 * Precompiles the routes of the TCK controllers.
 */
@PrecompiledHttpRoutes(packages = {
        "io.micronaut.http.server.tck.tests",
        "io.micronaut.http.server.tck.tests.binding",
        "io.micronaut.http.server.tck.tests.bodywritable",
        "io.micronaut.http.server.tck.tests.codec",
        "io.micronaut.http.server.tck.tests.constraintshandler",
        "io.micronaut.http.server.tck.tests.cors",
        "io.micronaut.http.server.tck.tests.endpoints",
        "io.micronaut.http.server.tck.tests.endpoints.health",
        "io.micronaut.http.server.tck.tests.exceptions",
        "io.micronaut.http.server.tck.tests.filter",
        "io.micronaut.http.server.tck.tests.filter.options",
        "io.micronaut.http.server.tck.tests.forms",
        "io.micronaut.http.server.tck.tests.hateoas",
        "io.micronaut.http.server.tck.tests.jsonview",
        "io.micronaut.http.server.tck.tests.mediatype",
        "io.micronaut.http.server.tck.tests.raw",
        "io.micronaut.http.server.tck.tests.routing",
        "io.micronaut.http.server.tck.tests.staticresources",
        "io.micronaut.http.server.tck.tests.textplain"
})
public class Application {
}
