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
package io.micronaut.docs.aop.lifecycle

// tag::imports[]
import io.micronaut.context.annotation.Parameter

import jakarta.annotation.PreDestroy
// end::imports[]

// tag::class[]
@ProductBean // <1>
class Product(@Parameter val productName: String): // <2>
  var active: Boolean = false

  @PreDestroy // <3>
  def disable(): Unit =
    active = false
// end::class[]
