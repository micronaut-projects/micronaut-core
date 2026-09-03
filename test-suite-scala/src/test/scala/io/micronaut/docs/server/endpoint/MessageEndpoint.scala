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
package io.micronaut.docs.server.endpoint

// tag::endpointImport[]
import io.micronaut.context.annotation.Requires
import io.micronaut.management.endpoint.annotation.Endpoint
// end::endpointImport[]

// tag::mediaTypeImport[]
import io.micronaut.http.MediaType
// end::mediaTypeImport[]

// tag::writeImport[]
import io.micronaut.management.endpoint.annotation.Write
// end::writeImport[]

// tag::deleteImport[]
import io.micronaut.management.endpoint.annotation.Delete
// end::deleteImport[]

import io.micronaut.management.endpoint.annotation.Read
import jakarta.annotation.PostConstruct

@Requires(property = "spec.name", value = "MessageEndpointSpec")
// tag::endpointClassBegin[]
@Endpoint(id = "message", defaultSensitive = false)
class MessageEndpoint:
// end::endpointClassBegin[]

  // tag::message[]
  private var message: String | Null = null
  // end::message[]

  @PostConstruct
  def init(): Unit =
    message = "default message"

  @Read
  def currentMessage(): String | Null = message

  // tag::writeArg[]
  @Write(consumes = Array(MediaType.APPLICATION_FORM_URLENCODED), produces = Array(MediaType.TEXT_PLAIN))
  def updateMessage(newMessage: String): String =
    message = newMessage

    "Message updated"
  // end::writeArg[]

  // tag::simpleDelete[]
  @Delete
  def deleteMessage(): String =
    message = null

    "Message deleted"
  // end::simpleDelete[]

// tag::endpointClassEnd[]
// end::endpointClassEnd[]
