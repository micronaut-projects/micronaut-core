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
package io.micronaut.docs.http.client.bind.`type`

//tag::clazz[]
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.`type`.Argument
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.bind.ClientRequestUriContext
import io.micronaut.http.client.bind.TypedClientArgumentRequestBinder

import jakarta.inject.Singleton

@Singleton
class MetadataClientArgumentBinder extends TypedClientArgumentRequestBinder[Metadata]:

  override def argumentType(): Argument[Metadata] =
    Argument.of(classOf[Metadata])

  override def bind(
      context: ArgumentConversionContext[Metadata],
      uriContext: ClientRequestUriContext,
      value: Metadata,
      request: MutableHttpRequest[?]
  ): Unit =
    request.header("X-Metadata-Version", value.version.toString)
    request.header("X-Metadata-Deployment-Id", value.deploymentId.toString)
//end::clazz[]
