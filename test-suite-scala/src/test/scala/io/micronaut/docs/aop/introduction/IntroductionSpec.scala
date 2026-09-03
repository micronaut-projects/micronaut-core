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
package io.micronaut.docs.aop.introduction

import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IntroductionSpec:

  @Test
  def testStubIntroduction(): Unit =
    val context = ApplicationContext.run()
    try
      // tag::test[]
      val stubExample = context.getBean(classOf[StubExample])

      assertEquals(10, stubExample.number())
      // end::test[]

      assertTrue(stubExample.isInstanceOf[Intercepted])
      assertEquals(1, context.getBean(classOf[StubIntroduction]).invocations)
    finally
      context.close()

  @Test
  def genericIntroductionMethodsExposeResolvedMetadata(): Unit =
    val context = ApplicationContext.run()
    try
      val definition = context.getBeanDefinition(classOf[GenericStubExample])
      val findMethod = definition.getRequiredMethod("find")
      assertEquals(classOf[GenericBook], findMethod.getReturnType.getType)

      val findAllMethod = definition.getRequiredMethod("findAll")
      val findAllElementType = findAllMethod.getReturnType.asArgument().getTypeVariables.values().iterator().next().getType
      assertEquals(classOf[List[?]], findAllMethod.getReturnType.getType)
      assertEquals(classOf[GenericBook], findAllElementType)
    finally
      context.close()
