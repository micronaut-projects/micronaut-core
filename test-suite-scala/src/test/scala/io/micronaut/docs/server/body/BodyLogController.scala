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
package io.micronaut.docs.server.body

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import org.slf4j.Logger
import org.slf4j.LoggerFactory
// end::imports[]

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import scala.annotation.meta.field
import scala.annotation.meta.getter
import scala.annotation.meta.param

@Requires(property = "spec.name", value = "BodyLogFilterSpec")
// tag::clazz[]
@Controller("/person")
class BodyLogController:
  private val LOG: Logger = LoggerFactory.getLogger(classOf[BodyLogController])

  @Post("/")
  def create(@Body person: Person): Unit = // <1>
    LOG.info("Creating person {}", person)

@Introspected
case class Person @JsonCreator() (
    @(JsonProperty @param @field @getter)("firstName") firstName: String,
    @(JsonProperty @param @field @getter)("lastName") lastName: String
)
// end::clazz[]
