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
package io.micronaut.docs.factories.nullable

import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.core.util.Toggleable
import jakarta.validation.constraints.NotNull

// tag::class[]
@EachProperty("engines")
case class EngineConfiguration(
    @NotNull cylinders: Integer,
    @Bindable(defaultValue = "true") enabled: Boolean
) extends Toggleable:
  override def isEnabled: Boolean = enabled
// end::class[]
