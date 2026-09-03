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
package io.micronaut.docs.web.router.version

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
// end::imports[]

@Requires(property = "spec.name", value = "VersionedControllerSpec")
// tag::clazz[]
@Controller("/versioned")
class VersionedController:

  @Version("1") // <1>
  @Get("/hello")
  def helloV1(): String =
    "helloV1"

  @Version("2") // <2>
  @Get("/hello")
  def helloV2(): String =
    "helloV2"
// end::clazz[]

  @Version("2")
  @Get("/hello")
  def duplicatedHelloV2(): String =
    "duplicatedHelloV2"

  @Get("/hello")
  def hello(): String =
    "hello"
