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
package io.micronaut.docs.server.binding

import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import org.jspecify.annotations.Nullable

import scala.beans.BeanProperty

@Introspected
class PaginationCommand:

  @BeanProperty
  @PositiveOrZero
  @Nullable
  var offset: Integer | Null = null

  @BeanProperty
  @Positive
  @Nullable
  var max: Integer | Null = null

  @BeanProperty
  @Pattern(regexp = "name|href|title")
  @Nullable
  var sort: String | Null = null

  @BeanProperty
  @Pattern(regexp = "asc|desc|ASC|DESC")
  @Nullable
  var order: String | Null = null
