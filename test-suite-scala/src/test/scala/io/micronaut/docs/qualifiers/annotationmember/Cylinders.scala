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
package io.micronaut.docs.qualifiers.annotationmember

// tag::imports[]
import io.micronaut.context.annotation.NonBinding
import jakarta.inject.Qualifier

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import scala.annotation.StaticAnnotation
import scala.annotation.meta.getter
// end::imports[]

// tag::class[]
@Qualifier // <1>
@Retention(RetentionPolicy.RUNTIME)
class Cylinders(
    val value: Int,
    @(NonBinding @getter) val description: String = "" // <2>
) extends StaticAnnotation, java.lang.annotation.Annotation:
  override def annotationType(): Class[? <: java.lang.annotation.Annotation] =
    classOf[Cylinders]
// end::class[]
