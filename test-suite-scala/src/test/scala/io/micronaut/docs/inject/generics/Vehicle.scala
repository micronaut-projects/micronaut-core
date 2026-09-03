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
package io.micronaut.docs.inject.generics

import io.micronaut.context.annotation.Requires
import jakarta.inject.Inject
import jakarta.inject.Singleton

import java.util.List
import scala.compiletime.uninitialized

@Requires(property = "spec.name", value = "VehicleGenericsSpec")
@Singleton
class Vehicle(
    // tag::constructor[]
    private val engine: Engine[V8]
    // end::constructor[]
):
  private var v6EnginesValue: List[Engine[V6]] = uninitialized

  private var anotherV8: Engine[V8] = uninitialized

  def start(): String = engine.start()

  @Inject
  def setV6Engines(v6Engines: List[Engine[V6]]): Unit =
    this.v6EnginesValue = v6Engines

  def v6Engines: List[Engine[V6]] = v6EnginesValue

  @Inject
  def setAnotherV8(anotherV8: Engine[V8]): Unit =
    this.anotherV8 = anotherV8

  def getAnotherV8: Engine[V8] = anotherV8

  def getEngine: Engine[V8] = engine
