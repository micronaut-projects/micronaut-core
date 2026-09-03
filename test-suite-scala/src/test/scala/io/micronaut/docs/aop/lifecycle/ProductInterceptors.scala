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
import io.micronaut.aop.ConstructorInterceptor
import io.micronaut.aop.ConstructorInvocationContext
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.InterceptorKind
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Factory
// end::imports[]

// tag::class[]
@Factory
class ProductInterceptors(productService: ProductService):
// end::class[]

  // tag::constructor-interceptor[]
  @InterceptorBean(Array(classOf[ProductBean]))
  def aroundConstruct(): ConstructorInterceptor[Product] = // <1>
    (context: ConstructorInvocationContext[Product]) =>
      val parameterValues = context.getParameterValues // <2>
      val parameterValue = parameterValues(0)
      if parameterValue == null || parameterValue.toString.isEmpty then
        throw IllegalArgumentException("Invalid product name")
      val productName = parameterValue.toString.toUpperCase
      parameterValues(0) = productName
      val product = context.proceed() // <3>
      productService.addProduct(product)
      product
  // end::constructor-interceptor[]

  // tag::method-interceptor[]
  @InterceptorBean(Array(classOf[ProductBean])) // <1>
  def aroundInvoke(): MethodInterceptor[Product, Object] =
    (context: MethodInvocationContext[Product, Object]) =>
      val product = context.getTarget
      context.getKind match
        case InterceptorKind.POST_CONSTRUCT => // <2>
          product.active = true
          context.proceed()
        case InterceptorKind.PRE_DESTROY => // <3>
          productService.removeProduct(product)
          context.proceed()
        case _ =>
          context.proceed()
  // end::method-interceptor[]

// tag::class[]
// end::class[]
