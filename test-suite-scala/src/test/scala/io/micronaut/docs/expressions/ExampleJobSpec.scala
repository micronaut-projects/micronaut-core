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
package io.micronaut.docs.expressions

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class ExampleJobSpec:

  @Test
  def testJobCondition(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "ExampleJobSpec").asJava
    )
    try
      val exampleJob = context.getBean(classOf[ExampleJob])
      assertTrue(exampleJob.isPaused)
      assertFalse(exampleJob.hasJobRun)
      Thread.sleep(1500)
      assertFalse(exampleJob.hasJobRun)

      exampleJob.unpause()
      val deadline = System.nanoTime() + 4_000_000_000L
      while !exampleJob.hasJobRun && System.nanoTime() < deadline do
        Thread.sleep(100)
      assertTrue(exampleJob.hasJobRun)
    finally
      context.close()

  @Test
  def testUserDefinedEvaluationContext(): Unit =
    val context = ApplicationContext.run()
    try
      val consumer = context.getBean(classOf[ContextConsumer])
      assertTrue(consumer.randomField >= 1 && consumer.randomField < 10)
    finally
      context.close()
