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

import io.micronaut.core.annotation.Introspected

object ChristmasTypes:

  // tag::beans[]
  @Introspected
  case class ChristmasPresent(
      packagingColor: String,
      `type`: String,
      weight: Float,
      greetingCard: String
  )

  @Introspected
  case class PresentPackaging(
      weight: Float,
      color: String
  )

  @Introspected
  case class Present(
      weight: Float,
      `type`: String
  )
  // end::beans[]
