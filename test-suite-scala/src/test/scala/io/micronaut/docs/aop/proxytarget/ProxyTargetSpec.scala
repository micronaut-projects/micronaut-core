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
package io.micronaut.docs.aop.proxytarget

import io.micronaut.aop.HotSwappableInterceptedProxy
import io.micronaut.aop.InterceptedProxy
import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class ProxyTargetSpec:

  @Test
  def testProxyTarget(): Unit =
    val applicationContext = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "ProxyTargetSpec").asJava
    )
    try
      // tag::test[]
      val bean = applicationContext.getBean(classOf[ProxyTargetBean])
      val interceptor = applicationContext.getBean(classOf[MutatingInterceptor])

      assertTrue(bean.isInstanceOf[InterceptedProxy[?]])
      assertEquals("good", bean.someMethod())
      assertTrue(interceptor.invoked)

      val proxy = bean.asInstanceOf[InterceptedProxy[ProxyTargetBean]]
      assertFalse(bean.eq(proxy.interceptedTarget()))
      assertEquals(1, proxy.interceptedTarget().count)
      // end::test[]
    finally
      applicationContext.close()

  @Test
  def testHotswapProxyTarget(): Unit =
    val applicationContext = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "ProxyTargetSpec").asJava
    )
    try
      // tag::hotswapTest[]
      val bean = applicationContext.getBean(classOf[SwappableBean])
      val newTarget = new SwappableBean()

      assertTrue(bean.isInstanceOf[HotSwappableInterceptedProxy[?]])
      assertEquals("Name is test", bean.test("test"))

      val proxy = bean.asInstanceOf[HotSwappableInterceptedProxy[SwappableBean]]
      assertEquals(1, proxy.interceptedTarget().invocationCount)

      proxy.swap(newTarget)

      assertSame(newTarget, proxy.interceptedTarget())
      assertEquals(0, proxy.interceptedTarget().invocationCount)
      // end::hotswapTest[]
    finally
      applicationContext.close()
