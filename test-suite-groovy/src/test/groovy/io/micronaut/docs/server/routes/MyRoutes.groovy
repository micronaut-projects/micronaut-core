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
package io.micronaut.docs.server.routes

import io.micronaut.context.ExecutionHandleLocator
import io.micronaut.core.convert.ConversionService


// tag::imports[]
import io.micronaut.web.router.GroovyRouteBuilder

import javax.inject.Inject
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class MyRoutes extends GroovyRouteBuilder { // <1>

    MyRoutes(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService<?> conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService)
    }

    @Inject
    void issuesRoutes(IssuesController issuesController) { // <2>
        GET("/issues/show/{number}", issuesController.&issue) // <3>
    }
}
// end::class[]
