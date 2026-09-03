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

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LifeCycleAdviseSpec:

  @Test
  def testLifeCycleAdvise(): Unit =
    val applicationContext = ApplicationContext.run()
    try
      // tag::test[]
      val productService = applicationContext.getBean(classOf[ProductService])

      val product = applicationContext.createBean(classOf[Product], "Apple") // <1>
      assertTrue(product.active)
      assertTrue(productService.findProduct("APPLE").nonEmpty)

      applicationContext.destroyBean(product) // <2>
      assertFalse(product.active)
      assertFalse(productService.findProduct("APPLE").nonEmpty)
      // end::test[]
    finally
      applicationContext.close()
