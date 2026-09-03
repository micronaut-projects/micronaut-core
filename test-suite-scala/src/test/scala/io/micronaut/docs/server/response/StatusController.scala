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
package io.micronaut.docs.server.response

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Status

@Requires(property = "spec.name", value = "httpstatus")
@Controller("/status")
class StatusController:

  //tag::atstatus[]
  @Status(HttpStatus.CREATED)
  @Get(produces = Array(MediaType.TEXT_PLAIN))
  def index(): String =
    "success"
  //end::atstatus[]

  //tag::httpstatus[]
  @Get("/http-status")
  def httpStatus(): HttpStatus =
    HttpStatus.CREATED
  //end::httpstatus[]

  //tag::httpresponse[]
  @Get(value = "/http-response", produces = Array(MediaType.TEXT_PLAIN))
  def httpResponse(): HttpResponse[String] =
    HttpResponse.status[String](HttpStatus.CREATED).body("success")
  //end::httpresponse[]
