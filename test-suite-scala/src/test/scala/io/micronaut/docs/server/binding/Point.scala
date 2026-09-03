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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.ReflectiveAccess

import scala.annotation.meta.field
import scala.annotation.meta.getter
import scala.annotation.meta.param
import scala.beans.BeanProperty

@ReflectiveAccess
@Introspected
case class Point @JsonCreator() (
    @(JsonProperty @param @field @getter)("x") @BeanProperty x: Integer,
    @(JsonProperty @param @field @getter)("y") @BeanProperty y: Integer
)
