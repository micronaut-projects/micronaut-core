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
package io.micronaut.docs.aop.scheduled

import io.micronaut.context.ApplicationContext
import io.micronaut.scheduling.annotation.Scheduled
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters.*

class ScheduledExampleSpec:

  @Test
  def scheduledExamplesExposeScheduledMethods(): Unit =
    val context = ApplicationContext.run(
      Map[String, Object]("spec.name" -> "ScheduledExampleTest").asJava
    )
    try
      assertNotNull(context.getBean(classOf[ScheduledExample]))
      assertNotNull(context.getBean(classOf[TaskSchedulerInjectExample]).taskScheduler)

      val scheduledMethods = context.getBeanDefinition(classOf[ScheduledExample])
        .getExecutableMethods
        .asScala
        .filter(_.hasAnnotation(classOf[Scheduled]))
        .map(_.getMethodName)
        .toSet

      assertTrue(scheduledMethods.contains("everyFiveMinutes"))
      assertTrue(scheduledMethods.contains("fiveMinutesAfterLastExecution"))
      assertTrue(scheduledMethods.contains("everyMondayAtTenFifteenAm"))
      assertTrue(scheduledMethods.contains("onceOneMinuteAfterStartup"))
      assertTrue(scheduledMethods.contains("configuredTask"))
    finally
      context.close()
