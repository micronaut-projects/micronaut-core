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
package io.micronaut.docs.expressions

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "ExampleJobSpec")
// tag::clazz[]
@Singleton
class ExampleJob:
  private var jobRan = false
  private var paused = true

  @Scheduled(
    fixedRate = "1s",
    condition = "#{!this.paused}"
  ) // <1>
  def run(): Unit =
    println("Job Running")
    jobRan = true

  def isPaused: Boolean = paused // <2>

  def hasJobRun: Boolean = jobRan

  def unpause(): Unit =
    paused = false

  def pause(): Unit =
    paused = true
// end::clazz[]
