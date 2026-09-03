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
import io.micronaut.management.endpoint.annotation.Endpoint
// end::endpointImport[]

// tag::readImport[]
import io.micronaut.management.endpoint.annotation.Read
// end::readImport[]

// tag::mediaTypeImport[]
import io.micronaut.http.MediaType
import io.micronaut.management.endpoint.annotation.Selector
// end::mediaTypeImport[]

// tag::writeImport[]
import io.micronaut.management.endpoint.annotation.Write
// end::writeImport[]

import jakarta.annotation.PostConstruct

import java.util.Date
import scala.compiletime.uninitialized

// tag::endpointClassBegin[]
@Endpoint(
  id = "date",
  prefix = "custom",
  defaultEnabled = true,
  defaultSensitive = false
)
class CurrentDateEndpoint:
// end::endpointClassBegin[]

  // tag::methodSummary[]
  //.. endpoint methods
  // end::methodSummary[]

  // tag::currentDate[]
  private var currentDateValue: Date = uninitialized
  // end::currentDate[]

  @PostConstruct
  def init(): Unit =
    currentDateValue = Date()

  // tag::simpleRead[]
  @Read
  def currentDate(): Date = currentDateValue
  // end::simpleRead[]

  // tag::readArg[]
  @Read(produces = Array(MediaType.TEXT_PLAIN)) // <1>
  def currentDatePrefix(@Selector prefix: String): String =
    s"$prefix: $currentDateValue"
  // end::readArg[]

  // tag::simpleWrite[]
  @Write
  def reset(): String =
    currentDateValue = Date()

    "Current date reset"
  // end::simpleWrite[]
// tag::endpointClassEnd[]
// end::endpointClassEnd[]
