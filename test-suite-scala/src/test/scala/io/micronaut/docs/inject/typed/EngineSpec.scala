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
package io.micronaut.docs.inject.typed

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// tag::class[]
class EngineSpec:

  @Test
  def testEngine(): Unit =
    val beanContext = ApplicationContext.run()
    try
      assertThrows(
        classOf[NoSuchBeanException],
        () => beanContext.getBean(classOf[V8Engine]) // <1>
      )
      val engine = beanContext.getBean(classOf[Engine]) // <2>
      assertTrue(engine.isInstanceOf[V8Engine])
    finally beanContext.close()
// end::class[]
