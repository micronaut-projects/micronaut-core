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
package io.micronaut.docs.propagation

import io.micronaut.context.annotation.Requires
import io.micronaut.context.propagation.slf4j.MdcPropagationContext
import io.micronaut.core.propagation.PropagatedContext
import jakarta.inject.Singleton
import org.slf4j.MDC

import java.util.UUID

@Requires(property = "mdc.example.service.enabled")
@Singleton
class MdcService:

  // tag::createUser[]
  def createUser(name: String): String =
    try
      val newUserId = UUID.randomUUID()
      MDC.put("userId", newUserId.toString)
      PropagatedContext.getOrEmpty
        .plus(MdcPropagationContext())
        .propagate(() => createUserInternal(newUserId, name))
    finally
      MDC.remove("userId")
  // end::createUser[]

  private def createUserInternal(id: UUID, name: String): String =
    if MDC.get("userId") == null then
      throw IllegalStateException("Missing userId")
    s"New user id: $id name: $name"
