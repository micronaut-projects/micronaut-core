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
package io.micronaut.docs.ioc.mappers

import io.micronaut.context.annotation.Requires
import io.micronaut.docs.ioc.mappers.ChristmasTypes.ChristmasPresent
import io.micronaut.docs.ioc.mappers.ChristmasTypes.Present
import io.micronaut.docs.ioc.mappers.ChristmasTypes.PresentPackaging

//tag::imports[]
import io.micronaut.context.annotation.Mapper
import io.micronaut.context.annotation.Mapper.Mapping
//end::imports[]

@Requires(property = "spec.name", value = "MappersSpec")
//tag::mapper[]
trait ChristmasMappers:

  @Mapper(
    value = Array(
      new Mapping(from = "packaging.color", to = "packagingColor"),
      new Mapping(from = "#{packaging.weight + present.weight}", to = "weight"),
      new Mapping(from = "#{'Merry christmas'}", to = "greetingCard")
    )
  )
  def merge(packaging: PresentPackaging, present: Present): ChristmasPresent

//end::mapper[]
