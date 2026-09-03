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
import io.micronaut.aop.AroundConstruct
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorBindingDefinitions
import io.micronaut.aop.InterceptorKind
import io.micronaut.context.annotation.Prototype

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import scala.annotation.StaticAnnotation
// end::imports[]

// tag::class[]
@Retention(RetentionPolicy.RUNTIME)
@AroundConstruct // <1>
@InterceptorBindingDefinitions(Array(
  new InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT), // <2>
  new InterceptorBinding(kind = InterceptorKind.PRE_DESTROY) // <3>
))
@Prototype // <4>
class ProductBean extends StaticAnnotation, java.lang.annotation.Annotation:
  override def annotationType(): Class[? <: java.lang.annotation.Annotation] =
    classOf[ProductBean]
// end::class[]
