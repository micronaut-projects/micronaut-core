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
package io.micronaut.docs.requires

import io.micronaut.context.annotation.Requires

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import javax.sql.DataSource
import scala.annotation.StaticAnnotation

// tag::annotation[]
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(Array(ElementType.PACKAGE, ElementType.TYPE))
@Requires(beans = Array(classOf[DataSource]))
@Requires(property = "datasource.url")
class RequiresJdbc extends StaticAnnotation, java.lang.annotation.Annotation:
  override def annotationType(): Class[? <: java.lang.annotation.Annotation] =
    classOf[RequiresJdbc]
// end::annotation[]
