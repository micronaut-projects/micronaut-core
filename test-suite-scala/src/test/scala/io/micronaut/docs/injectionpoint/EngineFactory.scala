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
package io.micronaut.docs.injectionpoint

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype
import io.micronaut.inject.InjectionPoint

// end::class[]
@Factory
class EngineFactory:

  @Prototype
  def v8Engine(injectionPoint: InjectionPoint[?], crankShaft: CrankShaft): Engine = // <1>
    val cylinders = injectionPoint
      .getAnnotationMetadata
      .intValue(classOf[Cylinders]).orElse(8) // <2>
    cylinders match // <3>
      case 6 => V6Engine(crankShaft)
      case 8 => V8Engine(crankShaft)
      case _ => throw IllegalArgumentException("Unsupported number of cylinders specified: " + cylinders)
// tag::class[]
