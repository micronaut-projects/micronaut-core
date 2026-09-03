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
package io.micronaut.docs.writable

//tag::imports[]
import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import io.micronaut.core.io.Writable
import io.micronaut.core.util.CollectionUtils
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.exceptions.HttpServerException
//end::imports[]

//tag::clazz[]
@Controller("/template")
class TemplateController:

  private val templateEngine = SimpleTemplateEngine()
  private val template = initTemplate() // <1>

  @Get(value = "/welcome", produces = Array(MediaType.TEXT_PLAIN))
  def render(): Writable = // <2>
    writer =>
      template.make( // <3>
        CollectionUtils.mapOf(
          "firstName", "Fred",
          "lastName", "Flintstone"
        )
      ).writeTo(writer)

  private def initTemplate(): Template =
    try
      templateEngine.createTemplate(
        "Dear $firstName $lastName. Nice to meet you."
      )
    catch
      case e: Exception => throw HttpServerException("Cannot create template", e)
//end::clazz[]
