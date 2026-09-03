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
package io.micronaut.docs.http.client.bind.annotation

//tag::clazz[]
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.util.StringUtils
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.bind.AnnotatedClientArgumentRequestBinder
import io.micronaut.http.client.bind.ClientRequestUriContext

import jakarta.inject.Singleton
import java.util.Map
import scala.jdk.CollectionConverters.*

@Singleton
class MetadataClientArgumentBinder extends AnnotatedClientArgumentRequestBinder[Metadata]:

  override def getAnnotationType: Class[Metadata] = classOf[Metadata]

  override def bind(
      context: ArgumentConversionContext[Object],
      uriContext: ClientRequestUriContext,
      value: Object,
      request: MutableHttpRequest[?]
  ): Unit =
    value match
      case map: Map[?, ?] =>
        map.asScala.foreach { case (key, value) =>
          val headerName = NameUtils.hyphenate(StringUtils.capitalize(key.toString), false)
          request.header(s"X-Metadata-$headerName", value.toString)
        }
      case _ =>
//end::clazz[]
        ()
