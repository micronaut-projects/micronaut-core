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
package io.micronaut.docs.server.routes

// tag::imports[]
import io.micronaut.context.ExecutionHandleLocator
import io.micronaut.context.annotation.Requires
import io.micronaut.web.router.DefaultRouteBuilder
import io.micronaut.web.router.RouteBuilder

import jakarta.inject.Inject
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "IssuesControllerTest")
// tag::class[]
@Singleton
class MyRoutes(
    executionHandleLocator: ExecutionHandleLocator,
    uriNamingStrategy: RouteBuilder.UriNamingStrategy
) extends DefaultRouteBuilder(executionHandleLocator, uriNamingStrategy): // <1>

  @Inject
  def issuesRoutes(issuesController: IssuesController): Unit = // <2>
    GET("/issues/show/{number}", issuesController, "issue", classOf[Integer]) // <3>
// end::class[]
