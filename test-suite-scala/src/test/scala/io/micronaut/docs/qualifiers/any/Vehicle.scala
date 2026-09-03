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
package io.micronaut.docs.qualifiers.any

import io.micronaut.context.annotation.Requires
import io.micronaut.docs.qualifiers.annotationmember.Engine

// tag::imports[]
import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Any
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "VehicleAnySpec")
// tag::clazz[]
@Singleton
class Vehicle(@Any val engineProvider: BeanProvider[Engine]): // <1>
  def start(): Unit =
    engineProvider.ifPresent(_.start()) // <2>
// end::clazz[]

  // tag::startAll[]
  def startAll(): Unit =
    if engineProvider.isPresent then // <1>
      engineProvider.stream().forEach(_.start()) // <2>
  // end::startAll[]
